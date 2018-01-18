Kubernetes Cloud Plugin for Elasticsearch:
=========================================

[![Build Status](https://travis-ci.org/fabric8io/elasticsearch-cloud-kubernetes.svg?branch=master)](https://travis-ci.org/fabric8io/elasticsearch-cloud-kubernetes)

The Kubernetes Cloud plugin allows to use Kubernetes API for the unicast discovery mechanism.

Installation
============
```
elasticsearch-plugin install io.fabric8:elasticsearch-cloud-kubernetes:6.1.2
```

Versions available
==================

As of `2.0.0`, each released version of this plugin is compatible with the equal version of Elasticsearch,
e.g. `2.2.0` of this plugin is compatible with `2.2.0` of Elasticsearch.

**Older versions**:
* `1.3.x` for Elasticsearch `1.6` and `1.7`

Kubernetes Pod Discovery
===============================

Kubernetes Pod discovery allows to use the kubernetes APIs to perform automatic discovery.
Here is a simple sample configuration:

```yaml
cloud:
  kubernetes:
    service: ${SERVICE}
    namespace: ${NAMESPACE}
discovery.zen.hosts_provider: kubernetes

path:
  data: /data/data
  logs: /data/log
  plugins: /data/plugins
  work: /data/work
```

## Kubernetes auth

The preferred way to authenticate to Kubernetes is to use [service accounts](https://github.com/GoogleCloudPlatform/kubernetes/blob/master/docs/design/service_accounts.md).
This is fully supported by this Elasticsearch Kubernetes plugin as it uses the [Fabric8](http://fabric8.io) Kubernetes API client.

As an example, to create a service account do something like:

```
cat <<EOF | kubectl create -f -
apiVersion: v1
kind: ServiceAccount
metadata:
  name: elasticsearch
EOF
```

This creates a service account called `elasticsearch`. To use this service account, you need to add the service account
to your pod spec or your replication controller pod template spec like this:

```
apiVersion: v1
kind: Pod
metadata:
  name: elasticsearch
  <SNIP>
spec:
  serviceAccountName: elasticsearch
  <SNIP>
```

This will mount the service account token at `/var/run/secrets/kubernetes.io/servicaccount/token` & will be automatically
read by the fabric8 kubernetes client when the request is made to the API server.

# Kubernetes example

In this example, we're going to use a headless service to look up the Elasticsearch cluster nodes to join.

The following manifest uses 3 replication controllers to created Elasticsearch pods in 3 different modes:

* master
* data
* client

We use 2 services as well:

* One to target the client pods so that all requests to the cluster go through the client nodes
* A headless service to list all cluster endpoints managed by all 3 RCs.

```yaml
kind: "List"
apiVersion: "v1"
items:
- apiVersion: "v1"
  kind: "Service"
  metadata:
    name: "elasticsearch"
  spec:
    ports:
      - port: 9200
        targetPort: "http"
    selector:
      component: "elasticsearch"
      type: "client"
      provider: "fabric8"
    type: "LoadBalancer"
- apiVersion: "v1"
  kind: "Service"
  metadata:
    name: "elasticsearch-cluster"
  spec:
    clusterIP: "None"
    ports:
      - port: 9300
        targetPort: 9300
    selector:
      provider: "fabric8"
      component: "elasticsearch"
- apiVersion: "v1"
  kind: "ReplicationController"
  metadata:
    name: "elasticsearch-data"
  spec:
    replicas: 1
    selector:
      component: "elasticsearch"
      type: "data"
      provider: "fabric8"
    template:
      metadata:
        labels:
          component: "elasticsearch"
          type: "data"
          provider: "fabric8"
      spec:
        serviceAccount: elasticsearch
        serviceAccountName: elasticsearch
        containers:
          - env:
              - name: "SERVICE"
                value: "elasticsearch-cluster"
              - name: "KUBERNETES_NAMESPACE"
                valueFrom:
                  fieldRef:
                    fieldPath: "metadata.namespace"
              - name: "NODE_MASTER"
                value: "false"
            image: "fabric8/elasticsearch-k8s:6.1.2"
            name: "elasticsearch"
            ports:
              - containerPort: 9300
                name: "transport"
            volumeMounts:
              - mountPath: "/usr/share/elasticsearch/data"
                name: "elasticsearch-data"
                readOnly: false
        volumes:
          - emptyDir:
              medium: ""
            name: "elasticsearch-data"
- apiVersion: "v1"
  kind: "ReplicationController"
  metadata:
    name: "elasticsearch-master"
  spec:
    replicas: 1
    selector:
      component: "elasticsearch"
      type: "master"
      provider: "fabric8"
    template:
      metadata:
        labels:
          component: "elasticsearch"
          type: "master"
          provider: "fabric8"
      spec:
        serviceAccount: elasticsearch
        serviceAccountName: elasticsearch
        containers:
          - env:
              - name: "SERVICE"
                value: "elasticsearch-cluster"
              - name: "KUBERNETES_NAMESPACE"
                valueFrom:
                  fieldRef:
                    fieldPath: "metadata.namespace"
              - name: "NODE_DATA"
                value: "false"
            image: "fabric8/elasticsearch-k8s:6.1.2"
            name: "elasticsearch"
            ports:
              - containerPort: 9300
                name: "transport"
- apiVersion: "v1"
  kind: "ReplicationController"
  metadata:
    name: "elasticsearch-client"
  spec:
    replicas: 1
    selector:
      component: "elasticsearch"
      type: "client"
      provider: "fabric8"
    template:
      metadata:
        labels:
          component: "elasticsearch"
          type: "client"
          provider: "fabric8"
      spec:
        serviceAccount: elasticsearch
        serviceAccountName: elasticsearch
        containers:
          - env:
              - name: "SERVICE"
                value: "elasticsearch-cluster"
              - name: "KUBERNETES_NAMESPACE"
                valueFrom:
                  fieldRef:
                    fieldPath: "metadata.namespace"
              - name: "NODE_DATA"
                value: "false"
              - name: "NODE_MASTER"
                value: "false"
            image: "fabric8/elasticsearch-k8s:6.1.2"
            name: "elasticsearch"
            ports:
              - containerPort: 9200
                name: "http"
              - containerPort: 9300
                name: "transport"
```
