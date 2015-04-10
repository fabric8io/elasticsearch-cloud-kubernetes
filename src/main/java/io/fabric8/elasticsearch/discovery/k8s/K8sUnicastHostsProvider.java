package io.fabric8.elasticsearch.discovery.k8s;

import io.fabric8.kubernetes.api.Kubernetes;
import io.fabric8.kubernetes.api.KubernetesFactory;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Port;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.discovery.zen.ping.unicast.UnicastHostsProvider;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class K8sUnicastHostsProvider extends AbstractComponent implements UnicastHostsProvider {

    static final class Status {
        private static final String RUNNING = "Running";
    }

    static final public class Fields {
        public static final String REFRESH = "refresh_interval";
        public static final String VERSION = "Elasticsearch/K8sCloud/1.0";
        public static final String SELECTOR = "selector";
        public static final String SERVICE_DNS = "servicedns";
    }

    private Kubernetes kubernetes;

    private TransportService transportService;
    private NetworkService networkService;

    private final String selector;
    private final String serviceDns;

    private final TimeValue refreshInterval;
    private long lastRefresh;
    private List<DiscoveryNode> cachedDiscoNodes;

    @Inject
    public K8sUnicastHostsProvider(Settings settings,
                                   TransportService transportService,
                                   NetworkService networkService) {
        super(settings);
        this.transportService = transportService;
        this.networkService = networkService;

        this.refreshInterval = componentSettings.getAsTime(Fields.REFRESH,
                settings.getAsTime("cloud.k8s." + Fields.REFRESH, TimeValue.timeValueSeconds(0)));

        this.selector = componentSettings.get(Fields.SELECTOR, settings.get("cloud.k8s." + Fields.SELECTOR));
        this.serviceDns = componentSettings.get(Fields.SELECTOR, settings.get("cloud.k8s." + Fields.SERVICE_DNS));

        // Check that we have all needed properties
        if (!(Strings.hasText(this.selector) || Strings.hasText(this.serviceDns))) {
            logger.warn("Neither cloud.k8s.{} or cloud.k8s.{} are set.", Fields.SELECTOR, Fields.SERVICE_DNS);
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
        if (refreshInterval.millis() != 0) {
            if (cachedDiscoNodes != null &&
                    (refreshInterval.millis() < 0 || (System.currentTimeMillis() - lastRefresh) < refreshInterval.millis())) {
                if (logger.isTraceEnabled()) logger.trace("using cache to retrieve node list");
                return cachedDiscoNodes;
            }
            lastRefresh = System.currentTimeMillis();
        }
        logger.debug("start building nodes list using Kubernetes API");

        cachedDiscoNodes = Lists.newArrayList();
        String currentIpAddress = null;
        try {
            InetAddress inetAddress = networkService.resolvePublishHostAddress(null);
            if (inetAddress != null) {
                currentIpAddress = inetAddress.getHostAddress();
            }
        } catch (IOException e) {
            // We can't find the publish host address... Hmmm. Too bad :-(
            // We won't simply filter it
        }

        try {
            if (Strings.hasText(this.selector)) {
                return getNodesFromKubernetesSelector(currentIpAddress);
            } else if (Strings.hasText(this.serviceDns)) {
                return getNodesFromKubernetesServiceDns(currentIpAddress);
            }
        } catch (Throwable e) {
            logger.warn("Exception caught during discovery {} : {}", e, e.getClass().getName(), e.getMessage());
            logger.trace("Exception caught during discovery", e);
        }

        logger.debug("{} node(s) added", cachedDiscoNodes.size());
        logger.debug("using dynamic discovery nodes {}", cachedDiscoNodes);

        return cachedDiscoNodes;
    }

    private List<DiscoveryNode> getNodesFromKubernetesSelector(String currentIpAddress) {
        Map<String, Pod> podMap = KubernetesHelper.getSelectedPodMap(getKubernetes(), selector);
        Collection<Pod> pods = podMap.values();

        if (pods == null) {
            logger.trace("no pod found for selector [{}].", this.selector);
            return cachedDiscoNodes;
        }

        for (Pod pod : pods) {
            String status = pod.getCurrentState().getStatus();
            logger.trace("k8s instance {} with status {} found.", pod.getId(), status);

            if (!status.equals(Status.RUNNING)) {
                logger.trace("k8s instance {} is not running - ignoring.", pod.getId());
                continue;
            }

            try {
                String podIp = pod.getCurrentState().getPodIP();

                if (podIp.equals(currentIpAddress)) {
                    // We found the current node.
                    // We can ignore it in the list of DiscoveryNode
                    logger.trace("current node found. Ignoring {} - {}", pod.getId(), podIp);
                } else {
                    List<Container> containers = KubernetesHelper.getContainers(pod);
                    for (Container container : containers) {
                        logger.trace("pod " + pod.getId() + " container: " + container.getName() + " image: " + container.getImage());
                        List<Port> ports = container.getPorts();
                        for (Port port : ports) {
                            Integer containerPort = port.getContainerPort();
                            if (containerPort == 9300) {
                                String address = podIp.concat(":").concat(containerPort.toString());

                                // pod IP is a single IP Address. We need to build a TransportAddress from it
                                TransportAddress[] addresses = transportService.addressesFromString(address);

                                logger.trace("adding {}, address {}, transport_address {}, status {}", pod.getId(),
                                        podIp, addresses[0], status);
                                cachedDiscoNodes.add(new DiscoveryNode("#cloud-" + pod.getId() + "-" + 0, addresses[0], Version.CURRENT));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("failed to add {}", e, pod.getId());
            }
        }

        return cachedDiscoNodes;
    }

    private List<DiscoveryNode> getNodesFromKubernetesServiceDns(String currentIpAddress) throws Exception {
        Set<String> serivceEndpointIps = KubernetesHelper.lookupServiceInDns(serviceDns);

        if (serivceEndpointIps == null) {
            logger.trace("no service endpoints found for service name [{}].", this.serviceDns);
            return cachedDiscoNodes;
        }

        for (String endpointIp : serivceEndpointIps) {
            if (endpointIp.equals(currentIpAddress)) {
                // We found the current node.
                // We can ignore it in the list of DiscoveryNode
                logger.trace("current node found. Ignoring {} - {}", endpointIp);
            } else {
                String address = endpointIp.concat(":9300");

                // pod IP is a single IP Address. We need to build a TransportAddress from it
                TransportAddress[] addresses = transportService.addressesFromString(address);

                logger.trace("adding address {}, transport_address {}",
                        endpointIp, addresses[0]);
                cachedDiscoNodes.add(new DiscoveryNode("#cloud-".concat(serviceDns).concat("-").concat(endpointIp) + "-" + 0, addresses[0], Version.CURRENT));
            }
        }

        return cachedDiscoNodes;
    }

    private Kubernetes getKubernetes() {
        if (kubernetes == null) {
            kubernetes = new KubernetesFactory().createKubernetes();
        }
        return kubernetes;
    }
}
