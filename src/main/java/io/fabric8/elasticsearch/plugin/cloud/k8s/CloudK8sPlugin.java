package io.fabric8.elasticsearch.plugin.cloud.k8s;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.AbstractPlugin;

/**
 *
 */
public class CloudK8sPlugin extends AbstractPlugin {

  private final Settings settings;

  public CloudK8sPlugin(Settings settings) {
    this.settings = settings;
  }

  @Override
  public String name() {
    return "cloud-kubernetes";
  }

  @Override
  public String description() {
    return "Cloud Kubernetes Plugin";
  }

}
