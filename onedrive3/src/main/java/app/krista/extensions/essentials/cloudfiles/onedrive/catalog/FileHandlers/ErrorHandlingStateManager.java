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

package app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers;

import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators.ValidationOrchestrator;
import app.krista.extensions.util.KeyValueStore;
import com.google.gson.reflect.TypeToken;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants.GSON;

@Service
public class ErrorHandlingStateManager {

    private final KeyValueStore internalState;

    @Inject
    public ErrorHandlingStateManager(KeyValueStore internalState) {
        this.internalState = internalState;
    }

    /**
     * Stores a value in the internal state store.
     * Used to persist validation results and operation state for retry scenarios.
     *
     * @param key The unique identifier for the stored value
     * @param value The value to store (typically validation results or operation state)
     */
    public void put(String key, Object value) {
        internalState.put(key, value);
    }

    /**
     * Retrieves validation results from the internal state store.
     * Deserializes the stored JSON string back into a list of ValidationResult objects.
     *
     * @param key The unique identifier for the stored validation results
     * @return List of ValidationResult objects, or null if key not found
     */
    public List<ValidationOrchestrator.ValidationResult> get(String key) {
        final String map = String.valueOf(internalState.get(key));
        return GSON.fromJson(map, TypeToken.getParameterized(List.class, ValidationOrchestrator.ValidationResult.class).getType());
    }

    /**
     * Retrieves a map of state data from the internal state store.
     * Deserializes the stored JSON string back into a Map object.
     *
     * @param key The unique identifier for the stored state map
     * @return Map containing state data, or null if key not found
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String key) {
        final String map = String.valueOf(internalState.get(key));
        return GSON.fromJson(map, Map.class);
    }

}
