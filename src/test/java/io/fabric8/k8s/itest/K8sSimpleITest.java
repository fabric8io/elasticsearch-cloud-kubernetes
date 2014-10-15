package io.fabric8.k8s.itest;

import io.fabric8.cloud.k8s.AbstractK8sTest;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.PluginsService;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.Test;

@AbstractK8sTest.K8sTest
@ElasticsearchIntegrationTest.ClusterScope(
        scope = ElasticsearchIntegrationTest.Scope.SUITE,
        numDataNodes = 1,
        transportClientRatio = 0.0)
public class K8sSimpleITest extends AbstractK8sTest {

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return ImmutableSettings.builder()
                .put(super.nodeSettings(nodeOrdinal))
                .put("plugins." + PluginsService.LOAD_PLUGIN_FROM_CLASSPATH, true)
                .build();
    }

    @Test
    public void one_node_should_run() {
        // Do nothing... Just start :-)
        // but let's check that we have at least 2 nodes (local node + 1 running in kube)
        ClusterStateResponse clusterState = client().admin().cluster().prepareState().execute().actionGet();

        System.out.println(clusterState.getState().getNodes().getSize());

        assertThat(clusterState.getState().getNodes().getSize(), Matchers.greaterThanOrEqualTo(2));
    }

    @Override
    public Settings indexSettings() {
        // During restore we frequently restore index to exactly the same state it was before, that might cause the same
        // checksum file to be written twice during restore operation
        return ImmutableSettings.builder().put(super.indexSettings())
                .build();
    }
}
