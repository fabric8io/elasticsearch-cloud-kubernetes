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
import java.util.List;

public class KubernetesUnicastHostsProvider extends AbstractComponent implements UnicastHostsProvider {

  private final Version version;
  private final String namespace;
  private final String serviceName;
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
      doPrivileged((PrivilegedAction) () -> {
        result.addAll(readNodes());
        return null;
      });

    return result;
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
      // We can't find the publish host address... Hmmm. Too bad :-(
      // We won't simply filter it
    }
    final String ipAddress = tmpIPAddress;

    try {
      Endpoints endpoints = kubernetesAPIService.endpoints();
      if (endpoints == null || endpoints.getSubsets() == null || endpoints.getSubsets().isEmpty()) {
        logger.warn("no endpoints found for service [{}], namespace [{}].", this.serviceName, this.namespace);
        return cachedDiscoNodes;
      }
      endpoints.getSubsets().stream().forEach((endpointSubset) -> {
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

                endpointSubset.getPorts().stream().forEach((port) -> {
                  try {
                    TransportAddress[] addresses = transportService.addressesFromString(formattedEndpointAddress + ":" + port.getPort(), 1);

                    for (TransportAddress transportAddress : addresses) {
                      logger.info("adding endpoint {}, transport_address {}", endpointAddress, transportAddress);
                      cachedDiscoNodes.add(new DiscoveryNode("#cloud-" + endpointAddress + "-" + 0, transportAddress, version.minimumCompatibilityVersion()));
                    }
                  } catch (Exception e) {
                    logger.warn("failed to add endpoint {}", e, endpointAddress);
                  }
                });
              }
            } catch (Exception e) {
              logger.warn("failed to add endpoint {}", e, endpointAddress);
            }
          } catch (UnknownHostException e) {
            logger.warn("Ignoring invalid endpoint IP address: {}", e, ip);
          }
        }));
      });
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
