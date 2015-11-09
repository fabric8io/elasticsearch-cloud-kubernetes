package io.fabric8.elasticsearch.cloud.kubernetes;

import org.elasticsearch.common.component.LifecycleComponent;

import java.net.InetAddress;
import java.util.Collection;

public interface KubernetesAPIService extends LifecycleComponent<KubernetesAPIService> {

  /**
   * Return a collection of IP addresses for the service endpoints
   *
   * @return a collection of IP addresses for the service endpoints
   */
  Collection<InetAddress> endpoints();

  final class Fields {
    public static final String NAMESPACE = "cloud.kubernetes.namespace";
    public static final String SERVICE_NAME = "cloud.kubernetes.service";
    public static final String REFRESH = "cloud.kubernetes.refresh_interval";
    public static final String VERSION = "Elasticsearch/KubernetesCloud/1.0";

    public static final String RETRY = "cloud.kubernetes.retry";
    public static final String MAXWAIT = "cloud.kubernetes.max_wait";
  }

}
