package org.joget.plugin.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.enterprise.MultirowFormBinder;

public class MultirowOrderingConditionFormBinder extends MultirowFormBinder{

    @Override
    public String getName() {
        return "Multirow Form Binder with Condition and Ordering";
    }

    @Override
    public String getVersion() {
        return "8.0.0";
    }

    @Override
    public String getDescription() {
        return "Multirow Form Binder with Condition and Ordering";
    }
    
    @Override
    public String getLabel() {
        return "Multirow Form Binder with Condition and Ordering";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }
    
    private Object[] appendValue(Object[] obj, Object newObj) {
        ArrayList<Object> temp = new ArrayList<Object>(Arrays.asList(obj));
        temp.add(newObj);
        return temp.toArray();
    }
    
    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        FormRowSet rows = new FormRowSet();
        String formDefId = getPropertyString("formDefId");
        String tableName = getTableName(formDefId);
        String propertyName = getFormPropertyName(tableName, getPropertyString("foreignKey"));
        String orderFieldId = getFormPropertyName(tableName, getPropertyString("orderField"));
        String ordering = getFormPropertyName(tableName, getPropertyString("ordering"));
        boolean orderingFlag = false;
        
        String condition = (propertyName != null && !propertyName.isEmpty()) ? " WHERE " + propertyName + " = ?" : "";
        Object[] paramsArray = new Object[]{primaryKey};
        
        Object[] conditions = (Object[]) getProperty("conditions");
        if (conditions != null && conditions.length > 0) {
            for (Object o : conditions) {
                Map filterMap = (HashMap) o;
                String fieldId = filterMap.get("fieldId").toString();
                String fieldPropertyName = getFormPropertyName(tableName, fieldId);
                String joinType = filterMap.get("joinType").toString();
                String fieldOperator = filterMap.get("operator").toString();
                String value = filterMap.get("value").toString();
                condition += joinType + " " + fieldPropertyName + " " + fieldOperator + " ?";
                paramsArray = appendValue(paramsArray, value);
            }
        }
        
        if(orderFieldId == null){
            orderFieldId = "dateCreated";
        }
        
        orderFieldId = orderFieldId.replace("customProperties.", "");
        
        if(ordering != null){
            if(ordering.equalsIgnoreCase("asc")){
                orderingFlag = false;
            }else if(ordering.equalsIgnoreCase("desc")){
                orderingFlag = true;
            }
        }
        try {
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            rows = formDataDao.find(formDefId, tableName, condition, paramsArray, orderFieldId, orderingFlag, null, null);
        } catch (Exception e) {
            LogUtil.error(MultirowFormBinder.class.getName(), e, "");
        }
        
        rows.setMultiRow(true);
        return rows;
    }

    @Override
    public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
        if (rows == null) {
            return null;
        }
        
        FormDataDao formDataDao = (FormDataDao) FormUtil.getApplicationContext().getBean("formDataDao");
        AppService appService = (AppService) FormUtil.getApplicationContext().getBean("appService");
        
        String retainExistingDataWhenStoring = getPropertyString("deleteExisting");
        String formDefId = getPropertyString("formDefId");
        String tableName = getTableName(formDefId);
        
        if (tableName != null) {
            Form parentForm = FormUtil.findRootForm(element);
            String primaryKeyValue = parentForm.getPrimaryKeyValue(formData);
            
            Map<String, FormRow> exist = new HashMap<String, FormRow>();
            
            //Check for deletion
            FormRowSet originalRowSet = load(element, primaryKeyValue, formData);
            if (originalRowSet != null && !originalRowSet.isEmpty()) {
                Collection<String> ids = new ArrayList<String>();
                for (FormRow r : originalRowSet) {
                    if (rows == null || (rows != null && !rows.contains(r))) {
                        ids.add(r.getId());
                    } else {
                        exist.put(r.getId(), r);
                    }
                }
                if (ids.size() > 0 && !retainExistingDataWhenStoring.equalsIgnoreCase("true")) {
                    formDataDao.delete(formDefId, tableName, ids.toArray(new String[ids.size()]));
                }
            }
            
            for (FormRow row : rows) {
                //Set foreign key
                String id = row.getId();
                if (id != null && exist.containsKey(id)) {
                    FormRow temp = new FormRow();
                    temp.putAll(row);
                    
                    row.putAll(exist.get(id));
                    row.putAll(temp);
                }
                row.put(getPropertyString("foreignKey"), primaryKeyValue);
                
                //set conditions
                //not to set value into the grid anymore as we have complex conditions now to be able to define equals, not equals, and, or operators
//                Object[] conditions = (Object[]) getProperty("conditions");
//                if (conditions != null && conditions.length > 0) {
//                    for (Object o : conditions) {
//                        Map filterMap = (HashMap) o;
//                        String fieldId = filterMap.get("fieldId").toString();
//                        //String fieldPropertyName = getFormPropertyName(tableName, fieldId);
//                        String value = filterMap.get("value").toString();
//                        row.put(fieldId, value);
//                    }
//                }
            }
            
            rows = appService.storeFormData(formDefId, tableName, rows, null);
        }
        return rows;
    }
    
    @Override
    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{appId, appVersion};
        String json = AppUtil.readPluginResource(getClass().getName(), "/properties/multirowConditionFormBinder.json", arguments, true, "messages/multirowConditionFormBinder");
        return json;
    }
}
