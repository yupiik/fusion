package io.yupiik.fusion.framework.build.api.json;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Mark a record member as the sink for unmapped data by other members.
 * Attribute must be a {@code Map<String, Object>}.
 * Map values will be {@code Object} friendly, ie compatible with {@link io.yupiik.fusion.json.internal.codec.ObjectJsonCodec}:
 * <ul>
 *     <li>{@link Object}</li>
 *     <li>{@link String}</li>
 *     <li>{@link Boolean}</li>
 *     <li>{@link java.math.BigDecimal}</li>
 *     <li>{@link java.util.List<Object>}</li>
 *     <li>{@link java.util.Map<String,Object>}</li>
 * </ul>
 * <p>
 * Important: you can only use it once per record.
 */
@Retention(SOURCE)
@Target(PARAMETER)
public @interface JsonOthers {
}
