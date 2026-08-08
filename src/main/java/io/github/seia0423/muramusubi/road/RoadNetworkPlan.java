package io.github.seia0423.muramusubi.road;

import java.util.List;

public record RoadNetworkPlan(List<RoadConnection> connections, int componentCount) {
    public RoadNetworkPlan {
        connections = List.copyOf(connections);
        if (componentCount < 0) {
            throw new IllegalArgumentException("componentCountは0以上である必要があります");
        }
    }

    public boolean isFullyConnected() {
        return componentCount <= 1;
    }
}
