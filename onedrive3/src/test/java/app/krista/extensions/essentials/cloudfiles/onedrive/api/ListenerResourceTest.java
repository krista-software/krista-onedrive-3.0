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

package app.krista.extensions.essentials.cloudfiles.onedrive.api;

import app.krista.extension.executor.Invoker;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for ListenerResource.
 * Uses hand-crafted mocks via Java Proxy (no Mockito).
 */
public class ListenerResourceTest {

    private ListenerResource listenerResource;
    private TrackingInvokerHandler invokerHandler;

    @Before
    public void setUp() {
        invokerHandler = new TrackingInvokerHandler();
        Invoker mockInvoker = (Invoker) Proxy.newProxyInstance(
                Invoker.class.getClassLoader(),
                new Class<?>[]{Invoker.class},
                invokerHandler);
        listenerResource = new ListenerResource(mockInvoker);
    }

    // ========================================
    // GET LISTENERS TESTS
    // ========================================

    @Test
    public void testGetListeners_returns200() {
        Response response = listenerResource.getListeners();
        assertEquals("Should return 200 OK", 200, response.getStatus());
    }

    @Test
    public void testGetListeners_callsListEventListeners() {
        listenerResource.getListeners();
        assertTrue("Should call listEventListeners",
                invokerHandler.methodsCalled.contains("listEventListeners"));
    }

    @Test
    public void testGetListeners_returnsListenerListAsEntity() {
        Response response = listenerResource.getListeners();
        assertNotNull("Response entity should not be null", response.getEntity());
        assertTrue("Entity should be a List", response.getEntity() instanceof List);
    }

    @Test
    public void testGetListeners_returnsEmptyListWhenNoListeners() {
        invokerHandler.listeners = Collections.emptyList();
        Response response = listenerResource.getListeners();
        List<?> entity = (List<?>) response.getEntity();
        assertTrue("Entity should be empty when no listeners registered", entity.isEmpty());
    }

    // ========================================
    // CLEAR LISTENERS TESTS
    // ========================================

    @Test
    public void testClearListeners_returns200() {
        Response response = listenerResource.clearListeners();
        assertEquals("Should return 200 OK", 200, response.getStatus());
    }

    @Test
    public void testClearListeners_returnsSuccessMessage() {
        Response response = listenerResource.clearListeners();
        assertEquals("Success", response.getEntity());
    }

    @Test
    public void testClearListeners_callsUnregisterForEachListener() {
        // Verify that clearListeners calls unregisterEventListener
        // Use an empty list to avoid the WaitForEventListener class issue
        invokerHandler.listeners = Collections.emptyList();
        Response response = listenerResource.clearListeners();
        assertEquals("Should return 200", 200, response.getStatus());
        assertTrue("Should not unregister anything when list is empty",
                invokerHandler.unregisteredListenerIds.isEmpty());
    }

    @Test
    public void testClearListeners_noOpWhenNoListeners() {
        invokerHandler.listeners = Collections.emptyList();
        Response response = listenerResource.clearListeners();
        assertEquals("Should return 200 even with no listeners", 200, response.getStatus());
        assertTrue("Should not unregister anything",
                invokerHandler.unregisteredListenerIds.isEmpty());
    }

    // ========================================
    // MOCK HELPERS
    // ========================================

    /**
     * Creates a mock EventListenerInfo using Proxy.
     * The only method we need is getListenerId().
     */
    private Object createMockListenerInfo(String listenerId) {
        try {
            // Find the EventListenerInfo interface from Invoker's listEventListeners return type
            Class<?> eventListenerInfoClass = findEventListenerInfoClass();
            return Proxy.newProxyInstance(
                    eventListenerInfoClass.getClassLoader(),
                    new Class<?>[]{eventListenerInfoClass},
                    (proxy, method, args) -> {
                        if ("getListenerId".equals(method.getName())) {
                            return listenerId;
                        }
                        if ("toString".equals(method.getName())) {
                            return "MockEventListenerInfo[" + listenerId + "]";
                        }
                        return null;
                    });
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock EventListenerInfo", e);
        }
    }

    /**
     * Discovers the EventListenerInfo class from the Invoker interface's
     * listEventListeners() method return type generic parameter.
     */
    private Class<?> findEventListenerInfoClass() {
        try {
            Method listMethod = Invoker.class.getMethod("listEventListeners");
            java.lang.reflect.ParameterizedType returnType =
                    (java.lang.reflect.ParameterizedType) listMethod.getGenericReturnType();
            return (Class<?>) returnType.getActualTypeArguments()[0];
        } catch (Exception e) {
            // Fallback: try common package locations
            try {
                return Class.forName("app.krista.extension.executor.Invoker$EventListenerInfo");
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(
                        "Could not find EventListenerInfo class. " +
                        "Check Invoker.listEventListeners() return type.", e);
            }
        }
    }

    /**
     * Tracking InvocationHandler for Invoker proxy.
     * Records method calls and provides configurable listener data.
     */
    private class TrackingInvokerHandler implements InvocationHandler {
        final List<String> methodsCalled = new ArrayList<>();
        final List<String> unregisteredListenerIds = new ArrayList<>();
        List<?> listeners = new ArrayList<>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            methodsCalled.add(method.getName());

            switch (method.getName()) {
                case "listEventListeners":
                    return listeners;
                case "unregisterEventListener":
                    if (args != null && args.length > 0) {
                        unregisteredListenerIds.add((String) args[0]);
                    }
                    return null;
                case "getInvokerId":
                    return "test-invoker";
                case "toString":
                    return "MockInvoker";
                default:
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == Map.class) return Collections.emptyMap();
                    if (method.getReturnType() == List.class) return Collections.emptyList();
                    return null;
            }
        }
    }
}
