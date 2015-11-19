package io.fabric8.elasticsearch.cloud.kubernetes;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

public class KubernetesAPIServiceImpl extends AbstractLifecycleComponent<KubernetesAPIService>
  implements KubernetesAPIService {

  private final String namespace;
  private final String serviceName;

  @Override
  public Endpoints endpoints() {
    logger.debug("get endpoints for service {}, namespace {}", serviceName, namespace);
    return client().endpoints().inNamespace(namespace).withName(serviceName).get();
  }

  private KubernetesClient client;

  @Inject
  public KubernetesAPIServiceImpl(Settings settings) {
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
