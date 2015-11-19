package io.fabric8.elasticsearch.cloud.kubernetes;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.Settings;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class KubernetesAPIServiceImpl extends AbstractLifecycleComponent<KubernetesAPIService>
  implements KubernetesAPIService {

  private final String namespace;
  private final String serviceName;

  @Override
  public Collection<InetAddress> endpoints() {
    logger.debug("get endpoints for service {}, namespace {}", serviceName, namespace);
    final Set<InetAddress> instances = new HashSet<>();
    Endpoints endpoints = client().endpoints().inNamespace(namespace).withName(serviceName).get();
    if (endpoints != null && endpoints.getSubsets() != null) {
      endpoints.getSubsets().stream().forEach((endpointSubset) -> {
        endpointSubset.getAddresses().stream().forEach((endpointAddress -> {
          String ip = endpointAddress.getIp();
          try {
            instances.add(InetAddress.getByName(ip));
          } catch (UnknownHostException e) {
            logger.warn("Ignoring invalid endpoint IP address: {}", ip);
          }
        }));
      });
    }

    if (instances.isEmpty()) {
      logger.warn("disabling Kubernetes discovery. Can not get list of endpoints");
    }

    return instances;
  }

  private KubernetesClient client;

  @Inject
  public KubernetesAPIServiceImpl(Settings settings, NetworkService networkService) {
    super(settings);
    this.namespace = settings.get(Fields.NAMESPACE);
    this.serviceName = settings.get(Fields.SERVICE_NAME);
  }

  public synchronized KubernetesClient client() {
    if (client == null) {
      client = new DefaultKubernetesClient();
    }
    return client;
  }

  @Override
  protected void doStart() throws ElasticsearchException {
  }

  @Override
  protected void doStop() throws ElasticsearchException {
    if (client != null) {
      client.close();
    }
  }

  @Override
  protected void doClose() throws ElasticsearchException {
  }
}
