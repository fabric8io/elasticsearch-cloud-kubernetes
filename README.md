Kubernetes Cloud Plugin for Elasticsearch:
=========================================

[![Build Status](https://travis-ci.org/fabric8io/elasticsearch-cloud-kubernetes.svg?branch=master)](https://travis-ci.org/fabric8io/elasticsearch-cloud-kubernetes)

The Kubernetes Cloud plugin allows to use Kubernetes API for the unicast discovery mechanism.

## Version 1.0.1 for Elasticsearch: 1.4

Installation
============
```
plugin install io.fabric8/elasticsearch-cloud-kubernetes/1.0.1
```

Kubernetes Pod Discovery
===============================

Kubernetes Pod discovery allows to use the kubernetes APIs to perform automatic discovery.
Here is a simple sample configuration:

```yaml
cloud:
  k8s:
    selector: ${SELECTOR}
discovery:
    type: io.fabric8.elasticsearch.discovery.k8s.K8sDiscoveryModule

path:
  data: /data/data
  logs: /data/log
  plugins: /data/plugins
  work: /data/work
```

You can then set the `SELECTOR` environment variable to specify the selector for your Elasticsearch pods, e.g. `cluster=myCluster,component=elasticsearch`.
