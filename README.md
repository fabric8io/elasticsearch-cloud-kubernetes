Kubernetes Cloud Plugin for Elasticsearch:
=========================================

[![Build Status](https://travis-ci.org/fabric8io/elasticsearch-cloud-kubernetes.svg?branch=master)](https://travis-ci.org/fabric8io/elasticsearch-cloud-kubernetes)

The Kubernetes Cloud plugin allows to use Kubernetes API for the unicast discovery mechanism.

## Version 1.0.x for Elasticsearch: 1.4
## Version 1.1.x/1.2.x for Elasticsearch: 1.5
## Version 1.3.x for Elasticsearch: 1.6

Installation
============
```
plugin install io.fabric8/elasticsearch-cloud-kubernetes/1.2.1
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

If you want to use Kubernetes service endpoints instead (remember this will require auth to the
Kubernetes API server if you do this), then use:

```
service: ${SERVICE}
namespace: ${NAMESPACE}
```

Depending on which once you choose, you can then either:

* Set the `SERVICE` & `NAMESPACE` environment variables to specify the service to discover endpoints for, e.g. `elasticsearch-cluster`.
* Set the `SERVICE_DNS` environment variable to specify the service name to look up endpoints for in DNS, e.g. `elasticsearch-cluster`.

## Kubernetes auth

The preferred way to authenticate to Kubernetes is to use [service accounts](https://github.com/GoogleCloudPlatform/kubernetes/blob/master/docs/design/service_accounts.md).
This is fully supported by this Elasticsearch Kubernetes plugin as it uses the [Fabric8](http://fabric8.io) Kubernetes API client.

As an example, to create a service account do something like:

```
cat <<EOF | kubectl create -f -
apiVersion: v1beta3
kind: ServiceAccount
metadata:
  name: elasticsearch
EOF
```

This creates a service account called `elasticsearch`. To use this service account, you need to add the service account
to your pod spec or your replication controller pod template spec like this:

```
apiVersion: v1beta3
kind: Pod
metadata:
  name: elasticsearch
  <SNIP>
spec:
  serviceAccount: elasticsearch
  <SNIP>
```

This will mount the service account token at `/var/run/secrets/kubernetes.io/servicaccount/token` & will be automatically
read by the fabric8 kubernetes client when the request is made to the API server.

There are also a number of different environment variables that you may find useful:

. `KUBERNETES_TRUST_CERT=false`: disable certificate validation. Set to true to trust the Kubernetes API server certificate.
. `KUBECONFIG`: path to a standard Kubernetes config file to use for authentication configuration.

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
