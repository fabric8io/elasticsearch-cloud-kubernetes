package io.fabric8.elasticsearch.discovery.k8s;

import io.fabric8.kubernetes.api.Kubernetes;
import io.fabric8.kubernetes.api.KubernetesFactory;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ManifestContainer;
import io.fabric8.kubernetes.api.model.PodSchema;
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

import static io.fabric8.kubernetes.api.KubernetesHelper.getDockerIp;

public class K8sUnicastHostsProvider extends AbstractComponent implements UnicastHostsProvider {

    static final class Status {
        private static final String RUNNING = "Running";
    }

    static final public class Fields {
        public static final String REFRESH = "refresh_interval";
        public static final String VERSION = "Elasticsearch/K8sCloud/1.0";
        public static final String SELECTOR = "selector";
    }

    private Kubernetes kubernetes = new KubernetesFactory().createKubernetes();

    private TransportService transportService;
    private NetworkService networkService;

    private final String selector;

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

        // Check that we have all needed properties
        checkProperty(Fields.SELECTOR, selector);
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
        String ipAddress = null;
        try {
            InetAddress inetAddress = networkService.resolvePublishHostAddress(null);
            if (inetAddress != null) {
                ipAddress = inetAddress.getHostAddress();
            }
        } catch (IOException e) {
            // We can't find the publish host address... Hmmm. Too bad :-(
            // We won't simply filter it
        }

        try {
            Map<String, PodSchema> podMap = KubernetesHelper.getPodMap(kubernetes, selector);
            Collection<PodSchema> pods = podMap.values();

            if (pods == null) {
                logger.trace("no pod found for selector [{}].", this.selector);
                return cachedDiscoNodes;
            }

            for (PodSchema pod : pods) {
                String status = pod.getCurrentState().getStatus();
                logger.trace("k8s instance {} with status {} found.", pod.getId(), status);

                if (!status.equals(Status.RUNNING)) {
                    logger.trace("k8s instance {} is not running - ignoring.", pod.getId());
                    continue;
                }

                try {
                    String podIp = pod.getCurrentState().getPodIP();

                    if (podIp.equals(ipAddress)) {
                        // We found the current node.
                        // We can ignore it in the list of DiscoveryNode
                        logger.trace("current node found. Ignoring {} - {}", pod.getId(), podIp);
                    } else {
                        String address = pod.getCurrentState().getHost();

                        List<ManifestContainer> containers = KubernetesHelper.getContainers(pod);
                        for (ManifestContainer container : containers) {
                            logger.debug("pod " + pod.getId() + " container: " + container.getName() + " image: " + container.getImage());
                            List<Port> ports = container.getPorts();
                            for (Port port : ports) {
                                Integer containerPort = port.getContainerPort();
                                if (containerPort != null) {
                                    if (containerPort == 9300) {
                                        Integer hostPort = port.getHostPort();
                                        if (hostPort != null) {
                                            // if Kubernetes is running locally on a platform which doesn't support docker natively
                                            // then docker containers will be on a different IP so lets check for localhost and
                                            // switch to the docker IP if its available
                                            if (address.equals("localhost") || address.equals("127.0.0.1")) {
                                                String dockerIp = getDockerIp();
                                                if (io.fabric8.utils.Strings.isNotBlank(dockerIp)) {
                                                    address = dockerIp;
                                                }
                                            }

                                            address = address.concat(":").concat(hostPort.toString());

                                            // pod IP is a single IP Address. We need to build a TransportAddress from it
                                            TransportAddress[] addresses = transportService.addressesFromString(address);

                                            logger.trace("adding {}, address {}, transport_address {}, status {}", pod.getId(),
                                                    podIp, addresses[0], status);
                                            cachedDiscoNodes.add(new DiscoveryNode("#cloud-" + pod.getId() + "-" + 0, addresses[0], Version.CURRENT));
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("failed to add {}", e, pod.getId());
                }

            }
        } catch (Throwable e) {
            logger.warn("Exception caught during discovery {} : {}", e.getClass().getName(), e.getMessage());
            logger.trace("Exception caught during discovery", e);
        }

        logger.debug("{} node(s) added", cachedDiscoNodes.size());
        logger.debug("using dynamic discovery nodes {}", cachedDiscoNodes);

        return cachedDiscoNodes;
    }

    private void checkProperty(String name, String value) {
        if (!Strings.hasText(value)) {
            logger.warn("cloud.k8s.{} is not set.", name);
        }
    }
}
