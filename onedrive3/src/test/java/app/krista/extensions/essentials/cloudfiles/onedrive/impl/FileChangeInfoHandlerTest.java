/*
 * Onedrive 3.0 Extension for Krista
 * Copyright (C) 2025 Krista Software
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.krista.extensions.essentials.cloudfiles.onedrive.impl;

import app.krista.extension.executor.Invoker;
import app.krista.extension.request.RoutingInfo;
import app.krista.extension.request.protos.http.HttpProtocol;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import org.junit.Test;
import org.jvnet.hk2.annotations.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link FileChangeInfoHandler}.
 * Tests annotation presence and the routing URL replacement logic.
 */
public class FileChangeInfoHandlerTest {

    // ========================================
    // Annotation presence
    // ========================================

    @Test
    public void testClassIsAnnotatedWithService() {
        assertTrue("FileChangeInfoHandler should be annotated with @Service",
                FileChangeInfoHandler.class.isAnnotationPresent(Service.class));
    }

    // ========================================
    // Routing URL replacement logic
    // ========================================

    @Test
    public void testRoutingUrlReplacement_localExtnUrl_isReplaced() throws Exception {
        String localUrl = "https://extension.local.eng.krista.app/something";
        Invoker invoker = createMockInvoker(localUrl);
        GraphServiceClientProviderFactory factory = new GraphServiceClientProviderFactory(null, null, null, null);

        FileChangeInfoHandler handler = new FileChangeInfoHandler(factory, invoker);

        Field routingUrlField = FileChangeInfoHandler.class.getDeclaredField("routingUrl");
        routingUrlField.setAccessible(true);
        String actualRoutingUrl = (String) routingUrlField.get(handler);

        assertEquals("Local extension URL should be replaced with localhost",
                "http://localhost:8765/something", actualRoutingUrl);
    }

    @Test
    public void testRoutingUrlReplacement_nonLocalUrl_isNotReplaced() throws Exception {
        String productionUrl = "https://production.krista.app/mypath";
        Invoker invoker = createMockInvoker(productionUrl);
        GraphServiceClientProviderFactory factory = new GraphServiceClientProviderFactory(null, null, null, null);

        FileChangeInfoHandler handler = new FileChangeInfoHandler(factory, invoker);

        Field routingUrlField = FileChangeInfoHandler.class.getDeclaredField("routingUrl");
        routingUrlField.setAccessible(true);
        String actualRoutingUrl = (String) routingUrlField.get(handler);

        assertEquals("Non-local URL should remain unchanged",
                "https://production.krista.app/mypath", actualRoutingUrl);
    }

    // ========================================
    // getDeltaLinkToken returns empty string for null
    // ========================================

    @Test
    public void testGetDeltaLinkToken_nullDeltaPage_returnsEmptyString() throws Exception {
        String url = "https://production.krista.app";
        Invoker invoker = createMockInvoker(url);
        GraphServiceClientProviderFactory factory = new GraphServiceClientProviderFactory(null, null, null, null);
        FileChangeInfoHandler handler = new FileChangeInfoHandler(factory, invoker);

        java.lang.reflect.Method method = FileChangeInfoHandler.class.getDeclaredMethod(
                "getDeltaLinkToken",
                com.microsoft.graph.requests.DriveItemDeltaCollectionPage.class);
        method.setAccessible(true);

        String token = (String) method.invoke(handler, (Object) null);
        assertEquals("getDeltaLinkToken should return empty string for null input", "", token);
    }

    // ========================================
    // Mock helpers (hand-crafted, no Mockito)
    // ========================================

    private Invoker createMockInvoker(String routingUrl) {
        RoutingInfo routingInfo = (RoutingInfo) Proxy.newProxyInstance(
                RoutingInfo.class.getClassLoader(),
                new Class<?>[]{RoutingInfo.class},
                (proxy, method, args) -> {
                    if ("getRoutingURL".equals(method.getName())) return routingUrl;
                    return null;
                });
        return (Invoker) Proxy.newProxyInstance(
                Invoker.class.getClassLoader(),
                new Class<?>[]{Invoker.class},
                (proxy, method, args) -> {
                    if ("getInvokerId".equals(method.getName())) return "INV_test";
                    if ("getRoutingInfo".equals(method.getName())) return routingInfo;
                    if ("getAttributes".equals(method.getName())) return Collections.emptyMap();
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == Map.class) return Collections.emptyMap();
                    if (method.getReturnType() == List.class) return Collections.emptyList();
                    return null;
                });
    }
}
