/*
 * Copyright (c) 2022 - present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.fusion.testing.launcher;

import io.yupiik.fusion.framework.api.main.Launcher;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

class FusionCLITestExtension implements InvocationInterceptor, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(FusionCLITestExtension.class);

    @Override
    public void interceptTestMethod(final Invocation<Void> invocation,
                                    final ReflectiveInvocationContext<Method> invocationContext,
                                    final ExtensionContext context) throws Throwable {
        final var config = findAnnotation(context.getElement(), FusionCLITest.class).orElseThrow();
        final ByteArrayOutputStream out = config.captureStdOut() ? new ByteArrayOutputStream() : null;
        final ByteArrayOutputStream err = config.captureStderr() ? new ByteArrayOutputStream() : null;

        final var io = new IO(out, err, System.out, System.err);
        context.getStore(NAMESPACE).put(IO.class, io);
        if (io.out() != null) {
            System.setOut(new PrintStream(io.out()));
        }
        if (io.err() != null) {
            System.setErr(new PrintStream(io.err()));
        }
        try {
            Launcher.main(config.args());
        } finally {
            if (io.out() != null) {
                System.out.close(); // flush capture
                System.setOut(io.originalOut());
            }
            if (io.err() != null) {
                System.err.close(); // flush capture
                System.setErr(io.originalErr());
            }
        }
        invocation.proceed();
    }

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
        final var type = parameterContext.getParameter().getType();
        return type == Stdout.class || type == Stderr.class;
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext ctx) throws ParameterResolutionException {
        final var type = parameterContext.getParameter().getType();
        if (type == Stdout.class) {
            return new Stdout(() -> ctx.getStore(NAMESPACE).get(IO.class, IO.class).out().toString(UTF_8));
        }
        if (type == Stderr.class) {
            return new Stderr(() -> ctx.getStore(NAMESPACE).get(IO.class, IO.class).err().toString(UTF_8));
        }
        throw new ParameterResolutionException("Unknown type: " + type);
    }

    private record IO(ByteArrayOutputStream out, ByteArrayOutputStream err,
                      PrintStream originalOut, PrintStream originalErr) {
    }
}
