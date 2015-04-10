Kubernetes Cloud Plugin for Elasticsearch:
=========================================

[![Build Status](https://travis-ci.org/fabric8io/elasticsearch-cloud-kubernetes.svg?branch=master)](https://travis-ci.org/fabric8io/elasticsearch-cloud-kubernetes)

The Kubernetes Cloud plugin allows to use Kubernetes API for the unicast discovery mechanism.

## Version 1.0.x for Elasticsearch: 1.4
## Version 1.1.x for Elasticsearch: 1.5

Installation
============
```
plugin install io.fabric8/elasticsearch-cloud-kubernetes/1.1.0
```

Kubernetes Pod Discovery
===============================

Kubernetes Pod discovery allows to use the kubernetes APIs to perform automatic discovery.
Here is a simple sample configuration:

```yaml
cloud:
  k8s:
    servicedns: ${SERVICE_DNS}
discovery:
    type: io.fabric8.elasticsearch.discovery.k8s.K8sDiscoveryModule

path:
  data: /data/data
  logs: /data/log
  plugins: /data/plugins
  work: /data/work
```

If you want to use a Kubernetes pod selector instead, then use `selector: ${SELECTOR}` instead of the `serviceDns` setting.

Depending on which once you choose, you can then either:

* Set the `SELECTOR` environment variable to specify the selector for your Elasticsearch pods, e.g. `cluster=myCluster,component=elasticsearch`.
* Set the `SERVICE_DNS` environment variable to specify the service name to look up endpoints for in DNS, e.g. `elasticsearch-cluster`.

# Kubernetes example

In this example, we're going to use `servicedns` to look up the Elasticsearch cluster nodes to join.

The following manifest uses 3 replication controllers to created Elasticsearch pods in 3 different modes:

* master
* data
* client

We use 2 services as well:

* One to target the client pods so that all requests to the cluster go through the client nodes
* A headless service to list all cluster endpoints managed by all 3 RCs.

The DNS lookup is on the headless service name so that all existing nodes from the cluster are discovered on startup & the cluster is joined.

```json
{
  "id": "elasticsearch-list",
  "kind": "List",
  "apiVersion": "v1beta2",
  "name": "elasticsearch-config",
  "description": "Configuration for elasticsearch",
  "items": [
    {
      "id": "elasticsearch",
      "apiVersion": "v1beta1",
      "kind": "Service",
      "containerPort": 9200,
      "port": 9200,
      "selector": {
        "component": "elasticsearch",
        "type": "client",
        "provider": "fabric8"
      }
    },
    {
      "id": "elasticsearch-cluster",
      "apiVersion": "v1beta1",
      "PortalIP": "None",
      "kind": "Service",
      "containerPort": 9300,
      "port": 9300,
      "selector": {
        "component": "elasticsearch",
        "provider": "fabric8"
      }
    },
    {
      "id": "elasticsearch-client-rc",
      "kind": "ReplicationController",
      "apiVersion": "v1beta1",
      "desiredState": {
        "replicas": 1,
        "replicaSelector": {
          "component": "elasticsearch",
          "type": "client",
          "provider": "fabric8"
        },
        "podTemplate": {
          "desiredState": {
            "manifest": {
              "version": "v1beta1",
              "id": "elasticsearchPod",
              "containers": [
                {
                  "name": "elasticsearch-container",
                  "image": "fabric8/elasticsearch-k8s:1.5.0",
                  "imagePullPolicy": "PullIfNotPresent",
                  "env": [
                    {
                      "name": "SERVICE_DNS",
                      "value": "elasticsearch-cluster"
                    },
                    {
                      "name": "KUBERNETES_TRUST_CERT",
                      "value": "true"
                    },
                    {
                      "name": "NODE_DATA",
                      "value": "false"
                    },
                    {
                      "name": "NODE_MASTER",
                      "value": "false"
                    }
                  ],
                  "ports": [
                    {
                      "containerPort": 9200
                    },
                    {
                      "containerPort": 9300
                    }
                  ]
                }
              ]
            }
          },
          "labels": {
            "component": "elasticsearch",
            "type": "client",
            "provider": "fabric8"
          }
        }
      },
      "labels": {
        "component": "elasticsearch",
        "type": "client",
        "provider": "fabric8"
      }
    },
    {
      "id": "elasticsearch-data-rc",
      "kind": "ReplicationController",
      "apiVersion": "v1beta1",
      "desiredState": {
        "replicas": 1,
        "replicaSelector": {
          "component": "elasticsearch",
          "type": "data",
          "provider": "fabric8"
        },
        "podTemplate": {
          "desiredState": {
            "manifest": {
              "version": "v1beta1",
              "id": "elasticsearchPod",
              "containers": [
                {
                  "name": "elasticsearch-container",
                  "image": "fabric8/elasticsearch-k8s:1.5.0",
                  "imagePullPolicy": "PullIfNotPresent",
                  "env": [
                    {
                      "name": "SERVICE_DNS",
                      "value": "elasticsearch-cluster"
                    },
                    {
                      "name": "KUBERNETES_TRUST_CERT",
                      "value": "true"
                    },
                    {
                      "name": "NODE_MASTER",
                      "value": "false"
                    }
                  ],
                  "ports": [
                    {
                      "containerPort": 9300
                    }
                  ]
                }
              ]
            }
          },
          "labels": {
            "component": "elasticsearch",
            "type": "data",
            "provider": "fabric8"
          }
        }
      },
      "labels": {
        "component": "elasticsearch",
        "type": "data",
        "provider": "fabric8"
      }
    },
    {
      "id": "elasticsearch-master-rc",
      "kind": "ReplicationController",
      "apiVersion": "v1beta1",
      "desiredState": {
        "replicas": 1,
        "replicaSelector": {
          "component": "elasticsearch",
          "type": "master",
          "provider": "fabric8"
        },
        "podTemplate": {
          "desiredState": {
            "manifest": {
              "version": "v1beta1",
              "id": "elasticsearchPod",
              "containers": [
                {
                  "name": "elasticsearch-container",
                  "image": "fabric8/elasticsearch-k8s:1.5.0",
                  "imagePullPolicy": "PullIfNotPresent",
                  "env": [
                    {
                      "name": "SERVICE_DNS",
                      "value": "elasticsearch-cluster"
                    },
                    {
                      "name": "KUBERNETES_TRUST_CERT",
                      "value": "true"
                    },
                    {
                      "name": "NODE_DATA",
                      "value": "false"
                    }
                  ],
                  "ports": [
                    {
                      "containerPort": 9300
                    }
                  ]
                }
              ]
            }
          },
          "labels": {
            "component": "elasticsearch",
            "type": "master",
            "provider": "fabric8"
          }
        }
      },
      "labels": {
        "component": "elasticsearch",
        "type": "master",
        "provider": "fabric8"
      }
    }
  ]
}
```
