package test.p;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.build.api.json.JsonOthers;

@JsonModel
public record InvalidJsonOthers(@JsonOthers String others) {
}
