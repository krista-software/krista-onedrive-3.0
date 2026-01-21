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

package app.krista.extensions.essentials.cloudfiles.onedrive.catalog.stores.search;

import app.krista.extension.executor.SearchCondition;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.FileUtil;
import org.jetbrains.annotations.NotNull;

public class FileEntityCondition {

    private FileEntityCondition() {
    }

    public static boolean matchTextFieldCondition(String fieldName, String operator, String matchPattern) {
        if (fieldName == null || matchPattern == null) {
            return false;
        }
        switch (operator) {
            case Constants.EQUALS:
                return fieldName.equals(matchPattern);
            case Constants.STARTS_WITH:
                return fieldName.startsWith(matchPattern);
            case Constants.ENDS_WITH:
                return fieldName.endsWith(matchPattern);
            case Constants.CONTAINS:
                return fieldName.contains(matchPattern);
            case Constants.NOT_EQUALS:
                return !fieldName.equals(matchPattern);
            default:
                return false;
        }
    }

    public static boolean matchNumberFieldCondition(Number entityFieldValue, String operator, Number fieldValue) {

        switch (operator) {
            case Constants.GTE:
                return entityFieldValue instanceof Long ? ((Long) entityFieldValue >= (Long) fieldValue) : ((Double) entityFieldValue >= (Double) fieldValue);
            case Constants.LTE:
                return entityFieldValue instanceof Long ? ((Long) entityFieldValue <= (Long) fieldValue) : ((Double) entityFieldValue <= (Double) fieldValue);
            case Constants.GT:
                return entityFieldValue instanceof Long ? ((Long) entityFieldValue > (Long) fieldValue) : ((Double) entityFieldValue > (Double) fieldValue);
            case Constants.LT:
                return entityFieldValue instanceof Long ? ((Long) entityFieldValue < (Long) fieldValue) : ((Double) entityFieldValue < (Double) fieldValue);
            case Constants.EQUALS:
                return entityFieldValue instanceof Long ? FileUtil.isEqualDate((Long) entityFieldValue, (Long) fieldValue) : entityFieldValue.equals(fieldValue);
            default:
                return false;
        }
    }

    @NotNull
    public static String getOperator(SearchCondition condition) {
        return condition.getOperator() != null ? String.valueOf(condition.getOperator()).trim() : "";
    }

    public static double getSizeInDouble(SearchCondition condition) {
        return condition.getOperand() != null ? (Double) condition.getOperand() : 0;
    }

    @NotNull
    public static String getMatchPattern(SearchCondition condition) {
        return condition.getOperand() != null ? String.valueOf(condition.getOperand()).trim() : "";
    }
}
