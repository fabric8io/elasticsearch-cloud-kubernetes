package io.fabric8.elasticsearch.discovery.kubernetes;

import io.fabric8.elasticsearch.cloud.kubernetes.KubernetesAPIService;
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
import java.util.ArrayList;
import java.util.Collection;
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
    String ipAddress = null;
    try {
      InetAddress inetAddress = networkService.resolvePublishHostAddress(null);
      if (inetAddress != null) {
        ipAddress = NetworkAddress.formatAddress(inetAddress);
      }
    } catch (IOException e) {
      // We can't find the publish host address... Hmmm. Too bad :-(
      // We won't simply filter it
    }

    try {
      Collection<InetAddress> endpoints = kubernetesAPIService.endpoints();

      if (endpoints == null) {
        logger.trace("no endpoints found for service [{}], namespace [{}].", this.serviceName, this.namespace);
        return cachedDiscoNodes;
      }

      for (InetAddress endpoint : endpoints) {
        String endpointAddress = NetworkAddress.formatAddress(endpoint);
        try {
          if (endpointAddress.equals(ipAddress)) {
            // We found the current node.
            // We can ignore it in the list of DiscoveryNode
            logger.trace("current node found. Ignoring {}", ipAddress);
          } else {
            // endpoint address is a single IP Address. We need to build a TransportAddress from it
            endpointAddress = endpointAddress.concat(":9200");
            TransportAddress[] addresses = transportService.addressesFromString(endpointAddress, 1);

            for (TransportAddress transportAddress : addresses) {
              logger.trace("adding endpoint {}, transport_address {}", endpointAddress, transportAddress);
              cachedDiscoNodes.add(new DiscoveryNode("#cloud-" + endpointAddress + "-" + 0, transportAddress, version.minimumCompatibilityVersion()));
            }
          }
        } catch (Exception e) {
          logger.warn("failed to add endpoint {}", endpointAddress);
        }

      }
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
