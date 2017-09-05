/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.elasticsearch.discovery.kubernetes;

import io.fabric8.elasticsearch.cloud.kubernetes.KubernetesAPIService;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Pod;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.network.NetworkAddress;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.discovery.zen.ping.unicast.UnicastHostsProvider;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class KubernetesUnicastHostsProvider extends AbstractComponent implements UnicastHostsProvider {

  private final Version version;
  private final String namespace;
  private final String serviceName;
  private final String podLabel;
  private final int podPort;
  private final TimeValue refreshInterval;
  private final KubernetesAPIService kubernetesAPIService;
  private TransportService transportService;
  private NetworkService networkService;
  private long lastRefresh;
  private List<DiscoveryNode> cachedDiscoNodes;

  @Inject
  public KubernetesUnicastHostsProvider(Settings settings,
                                        KubernetesAPIService kubernetesAPIService,
                                        TransportService transportService,
                                        NetworkService networkService,
                                        Version version) {
    super(settings);
    this.transportService = transportService;
    this.networkService = networkService;
    this.kubernetesAPIService = kubernetesAPIService;
    this.version = version;

    this.refreshInterval = settings.getAsTime(KubernetesAPIService.Fields.REFRESH, TimeValue.timeValueSeconds(0));
    this.namespace = settings.get(KubernetesAPIService.Fields.NAMESPACE);
    this.serviceName = settings.get(KubernetesAPIService.Fields.SERVICE_NAME);
    this.podLabel = settings.get(KubernetesAPIService.Fields.POD_LABEL);
    if(settings.get(KubernetesAPIService.Fields.POD_PORT) != null) {
      this.podPort = Integer.parseInt(settings.get(KubernetesAPIService.Fields.POD_PORT));
    } else {
      this.podPort = 0;
    }
  }

  /**
   * We build the list of Nodes from Kubernetes API
   * Information can be cached using `plugins.refresh_interval` property if needed.
   * Setting `plugins.refresh_interval` to `-1` will cause infinite caching.
   * Setting `plugins.refresh_interval` to `0` will disable caching (default).
   */
  @Override
  public List<DiscoveryNode> buildDynamicNodes() {
    final List<DiscoveryNode> result = new ArrayList<>();
    AccessController.
      doPrivileged((PrivilegedAction<Void>) () -> {
        result.addAll(readNodes());
        return null;
      });

    return result;
  }
  
  private List<DiscoveryNode> getDiscoveryNodes(String formattedAddress, InetAddress address, int port) {
    try {
      TransportAddress[] addresses = transportService.addressesFromString(formattedAddress + ":" + port, 1);
      return Arrays.stream(addresses).map( transportAddress-> {
        logger.info("adding pod {}, transport_address {}", address, transportAddress);
        return new DiscoveryNode("#cloud-" + address + "-" + 0, transportAddress, version.minimumCompatibilityVersion());
      }).collect(Collectors.toList());
    } catch (Exception e) {
      logger.warn("failed to add endpoint {}", e, address);
      return new ArrayList<>();
    }
  }
  
  private List<DiscoveryNode> mapToDiscoveryNodes(List<Pod> pods, String ipAddress) {
  	List<DiscoveryNode> discoveryNodes = new ArrayList<>();
    pods.stream().forEach(pod -> {
      String ip = pod.getStatus().getPodIP();
      try {
        InetAddress podAddress = InetAddress.getByName(ip);
        String formattedPodAddress = NetworkAddress.format(podAddress);
        if (formattedPodAddress.equals(ipAddress)) {
          // We found the current node.
          // We can ignore it in the list of DiscoveryNode
          logger.trace("current node found. Ignoring {}", podAddress);
        } else {
        	discoveryNodes.addAll(getDiscoveryNodes(formattedPodAddress, podAddress, this.podPort));
        }
      } catch (UnknownHostException e) {
        logger.warn("Ignoring invalid pod IP address: {}", e, ip);
      }
    });
    return discoveryNodes;
  }

  private List<DiscoveryNode> mapToDiscoveryNodes(Endpoints endpoints, String ipAddress) {
    List<DiscoveryNode> discoveryNodes = new ArrayList<>();
    endpoints.getSubsets().stream().forEach(endpointSubset -> {
      endpointSubset.getAddresses().stream().forEach((address -> {
        String ip = address.getIp();
        try {
          InetAddress endpointAddress = InetAddress.getByName(ip);
          String formattedEndpointAddress = NetworkAddress.format(endpointAddress);
          try {
            if (formattedEndpointAddress.equals(ipAddress)) {
              // We found the current node.
              // We can ignore it in the list of DiscoveryNode
              logger.trace("current node found. Ignoring {}", ipAddress);
            } else {
            	discoveryNodes.addAll( endpointSubset.getPorts().stream()
              	.flatMap(port -> getDiscoveryNodes(formattedEndpointAddress, endpointAddress, port.getPort()).stream())
              	.collect(Collectors.toList()) );
            }
          } catch (Exception e) {
            logger.warn("failed to add endpoint {}", e, endpointAddress);
          }
        } catch (UnknownHostException e) {
          logger.warn("Ignoring invalid endpoint IP address: {}", e, ip);
        }
      }));
    });
    return discoveryNodes;
  }

  private List<DiscoveryNode> readNodes() {
    if (refreshInterval.millis() != 0) {
      if (cachedDiscoNodes != null &&
        (refreshInterval.millis() < 0 || (System.currentTimeMillis() - lastRefresh) < refreshInterval.millis())) {
        if (logger.isTraceEnabled()) logger.trace("using cache to retrieve node list");
        return cachedDiscoNodes;
      }
      lastRefresh = System.currentTimeMillis();
    }
    logger.debug("start building nodes list using Kubernetes API");

    cachedDiscoNodes = new ArrayList<>();
    String tmpIPAddress = null;
    try {
      InetAddress inetAddress = networkService.resolvePublishHostAddresses(null);
      if (inetAddress != null) {
        tmpIPAddress = NetworkAddress.format(inetAddress);
      }
    } catch (IOException e) {
    	logger.warn("Unable to find the publish host address", e);
      // We can't find the publish host address... Hmmm. Too bad :-(
      // We won't simply filter it
    }
    final String ipAddress = tmpIPAddress;

    try {
      final List<DiscoveryNode> discoveryNodes;
      if(this.podLabel != null) {
        List<Pod> pods = kubernetesAPIService.pods();
        if (pods == null || pods.isEmpty()) {
          logger.warn("no endpoints found for service [{}], namespace [{}].", this.serviceName, this.namespace);
          return cachedDiscoNodes;
        }
        discoveryNodes = mapToDiscoveryNodes(pods, ipAddress);
      } else {
      	Endpoints endpoints = kubernetesAPIService.endpoints();
        if (endpoints == null || endpoints.getSubsets() == null || endpoints.getSubsets().isEmpty()) {
          logger.warn("no endpoints found for service [{}], namespace [{}].", this.serviceName, this.namespace);
          return cachedDiscoNodes;
        }
        discoveryNodes = mapToDiscoveryNodes(endpoints, ipAddress);
      }
      cachedDiscoNodes.addAll(discoveryNodes);
    } catch (Throwable e) {
      logger.warn("Exception caught during discovery: {}", e, e.getMessage());
    }

    logger.debug("{} node(s) added", cachedDiscoNodes.size());
    logger.debug("using dynamic discovery nodes {}", cachedDiscoNodes);

    return cachedDiscoNodes;
  }

  static final class Status {
    private static final String TERMINATED = "TERMINATED";
  }
}
