Kubernetes Cloud Plugin for Elasticsearch
=========================================

The Kubernetes Cloud plugin allows to use Kubernetes API for the unicast discovery mechanism.

## Version 1.0.0 for Elasticsearch: 1.3

Kubernetes Pod Discovery
===============================

Kubernetes Pod discovery allows to use the kubernetes APIs to perform automatic discovery.
Here is a simple sample configuration:

```yaml
cloud:
  k8s:
      selector: name=elasticsearch
discovery:
      type: io.fabric8.discovery.k8s.K8sDiscoveryModule

path:
  data: /data/data
  logs: /data/log
  plugins: /data/plugins
  work: /data/work
```
