package test.p;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record JsonCycle(JsonCycle parent, String name) {
}