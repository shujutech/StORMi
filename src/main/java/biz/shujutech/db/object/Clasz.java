package biz.shujutech.db.object;

import biz.shujutech.reflect.ReflectField;
import biz.shujutech.base.App;
import biz.shujutech.base.Connection;
import biz.shujutech.base.DateAndTime;
import biz.shujutech.base.Hinderance;
import biz.shujutech.base.UnknownClasz;
import biz.shujutech.db.object.FieldClasz.FetchStatus;
import biz.shujutech.db.relational.Database;
import biz.shujutech.db.relational.Database.DbType;
import biz.shujutech.db.relational.Field;
import biz.shujutech.db.relational.FieldBoolean;
import biz.shujutech.db.relational.FieldDate;
import biz.shujutech.db.relational.FieldDateTime;
import biz.shujutech.db.relational.FieldInt;
import biz.shujutech.db.relational.FieldLong;
import biz.shujutech.db.relational.FieldFloat;
import biz.shujutech.db.relational.FieldStr;
import biz.shujutech.db.relational.FieldType;
import biz.shujutech.db.relational.PageDirection;
import biz.shujutech.db.relational.Record;
import biz.shujutech.db.relational.SortOrder;
import biz.shujutech.db.relational.Table;
import biz.shujutech.reflect.AttribField;
import biz.shujutech.reflect.AttribIndex;
import biz.shujutech.reflect.ReflectIndex;
import biz.shujutech.technical.ResultSetFetch;
import java.lang.reflect.Modifier;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.Comparator;
import org.joda.time.DateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import biz.shujutech.technical.Callback2ProcessObject;
import biz.shujutech.technical.Callback2ProcessResultSet;
import biz.shujutech.technical.LambdaGeneric;
import biz.shujutech.util.Generic;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import biz.shujutech.technical.Callback2ProcessMember;
import biz.shujutech.technical.Callback2ProcessMemberFreeType;

class ClassAndField {
	Class<?> nameClass;
	String nameField; 

	public ClassAndField(Class<?> aClass, String aField) {
		this.nameClass = aClass;
		this.nameField = aField;
	}
}

public class Clasz<Ty> extends Table implements Comparable<Ty> {

	public static final long NOT_INITIALIZE_OBJECT_ID = -1;
	public static final Long INSTANT_RECORD_AT = Long.valueOf(0); // instantiated objeck is always at this location in the table clszObject
	public static final String TABLE_NAME_PREFIX = "cz_";
	public static final String SEQUENCE_NAME_PREFIX = "sq_";
	public static final boolean PRE_CREATE_OBJECT= false; // if set to true, will create empty objects and place into FieldObject value
	public static final int RECURSIVE_DEPTH = 8;

	public static String CreateLeafClassColName(String aFieldName) {
		return aFieldName + "_" + ObjectBase.LEAF_CLASS;
	}

	private Clasz<?> parentObject = null;
	private Clasz<?> masterObject = null;
	public ObjectBase db = null;
	public CopyOnWriteArrayList<String> errorField = new CopyOnWriteArrayList<>();
	ConcurrentHashMap<String, Field> claszField = new ConcurrentHashMap<>();
	private boolean gotDeletedField = false;
	private boolean gotCreatedField = false;
	private Boolean forDelete = false;

	public void initBeforePopulate() throws Exception {};
	public void initBeforePopulate(Connection aConn) throws Exception {};
	public void setFieldByDisplayPosition() throws Exception {};

	@Override
	public ObjectBase getDb() {
		return db;
	}

	public void setDb(ObjectBase db) {
		this.db = db;
	}

	public void populateLookupField(Connection aConn) throws Exception {
		for(Field eachField : this.getTreeField().values()) {
			if (eachField.getDbFieldType() == FieldType.OBJECT) {
				FieldObject<?> objField = (FieldObject<?>) eachField;
				Class<?> clas = Class.forName(objField.getDeclareType());
				if (Lookup.class.isAssignableFrom(clas)) {
					if (objField.getValueObj(aConn) == null) {
						objField.createNewObject(aConn);
					}
				}
			}
		}
	}

	public boolean populate(Connection aConn) throws Exception {
		boolean result = false;
		if (fetchObjectSlow(aConn, this) != null) {
			result = true;
		}
		return(result);
	}

	public boolean deleteCommit(Connection aConn) throws Exception {
		boolean result = false;
		int totalDeleted = ObjectBase.DeleteCommit(aConn, this);
		if (totalDeleted > 0) result = true;
		return(result);
	}

	public boolean deleteNoCommit(Connection aConn) throws Exception {
		boolean result = false;
		int totalDeleted = ObjectBase.DeleteNoCommit(aConn, this);
		if (totalDeleted > 0) result = true;
		return(result);
	}

	/*
		This method will do commit for the object and restore the original autocommit status.
	*/
	public Long persistCommit(Connection aConn) throws Exception {
		Long result = ObjectBase.PersistCommit(aConn, this);
		return(result);
	}

	public Long persistNoCommit(Connection aConn) throws Exception {
		Long result = ObjectBase.PersistNoCommit(aConn, this);
		return(result);
	}

	public static void ClassAndFieldExistCheck(List<ClassAndField> aList, Class<?> aClass, String aField) {
		for(ClassAndField each : aList) {
			App.logInfo("Class: " + each.nameClass.getSimpleName() + ", field: " + each.nameField);
			if (each.nameClass == aClass && each.nameField.equals(aField)) {
			}
		}
	}

	public static boolean ClassAndFieldExist(List<ClassAndField> aList, Class<?> aClass, String aField) {
		boolean result = false;
		for(ClassAndField each : aList) {
			if (each.nameClass == aClass && each.nameField.equals(aField)) {
				result = true;
			}
		}
		return(result);
	}

	public Clasz() {
		super();
		this.setTableName(CreateTableName(this.getClass()));
		this.setUniqueIndexName(CreateUniqueIndexName(this.getClass()));
	}

	/**
	 * Resolves a {@link Clasz} subclass from a table name by inverting
	 * {@link #CreateTableName(Class)}: strip the table name prefix, convert the
	 * snake_case body back to CamelCase to recover the class simple name, then
	 * scan the classpath for the matching Clasz subclass. Returns {@code null}
	 * if the table name is malformed or no matching class is on the classpath.
	 */
	@SuppressWarnings("unchecked")
	public static Class<? extends Clasz<?>> GetClaszByTableName(String aTableName) throws Exception {
		if (aTableName == null) return null;
		String prefix = GetTableNamePrefix();
		if (aTableName.startsWith(prefix) == false) return null;
		String dbBody = aTableName.substring(prefix.length());
		String simpleClassName = Database.Db2JavaTableName(dbBody);
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader == null) loader = Clasz.class.getClassLoader();
		com.google.common.reflect.ClassPath cp = com.google.common.reflect.ClassPath.from(loader);
		for (com.google.common.reflect.ClassPath.ClassInfo ci : cp.getAllClasses()) {
			if (ci.getSimpleName().equals(simpleClassName) == false) continue;
			try {
				Class<?> c = ci.load();
				if (Clasz.class.isAssignableFrom(c) && c != Clasz.class) {
					return (Class<? extends Clasz<?>>) c;
				}
			} catch (Throwable ignore) {
				// skip classes that fail to load
			}
		}
		return null;
	}

	public Record getInstantRecord() {
		return(this.getRecordBox().get(Clasz.INSTANT_RECORD_AT));
	}

	/**
	 * By context meaning, this clasz is retrieved from db via it's parent
	 * 
	 * @return Clasz
	 */
	public Clasz<?> getParentObjectByContext() {
		return parentObject;
	}

	public void setParentObject(Clasz<?> parentObject) {
		this.parentObject = parentObject;
	}

	public Clasz<?> getMasterObject() {
		return masterObject;
	}

	public void setMasterObject(Clasz<?> masterObject) {
		this.masterObject = masterObject;
	}

	public static String GetInheritancePrefix() {
		return PREFIX_INHERITANCE;
	}

	public static String GetIvPrefix() {
		return PREFIX_MEMBER_OF;
	}

	public static String GetIwPrefix() {
		return PREFIX_MEMBER_BOX_OF;
	}

	public static String GetObjectIndexPrefix() {
		return PREFIX_OBJECT_INDEX;
	}

	public static String GetFieldIndexPrefix() {
		return PREFIX_FIELD_INDEX;
	}

	public static String GetPolymorphicPrefix() {
		return PREFIX_POLYMORPHIC;
	}

	public static String GetTableNamePrefix() {
		return TABLE_NAME_PREFIX;
	}

	public static String GetSequenceNamePrefix() {
		return SEQUENCE_NAME_PREFIX;
	}

	public static String CreateTableName(Clasz<?> aClasz) {
		return CreateTableName(aClasz.getClass());
	}

	public static String CreateTableName(Class<?> aClass) {
		String result = GetTableNamePrefix() + Database.Java2DbTableName(aClass.getSimpleName());
		return(result);
	}

	public String createSequenceTableName() {
		String result = GetSequenceNamePrefix() + Database.Java2DbTableName(this.getClass().getSimpleName());
		return(result);
	}

	public static String CreatePkColName(Class<?> aClass) throws Exception {
		String result = CreateTableName(aClass) + "_pk";
		return(result);
	}

	public static String CreateUniqueIndexName(Class<?> aClass) {  // change to defineRealWorldId
		String result = CreateTableName(aClass) + "_idx";
		return(result);
	}

	public void createFieldPk() throws Exception { // change to defineObjectId
		this.createFieldPk(this.getMetaRec());
	}

	public void createFieldPk(Record aRec) throws Exception {
		String pkName = this.tableName + "_pk";
		Field pkField = aRec.getField(pkName);
		if (pkField == null) {
			pkField = aRec.createField(this.tableName + "_pk", FieldType.LONG);
		}
		pkField.setPrimaryKey();
	}

	public String getChildCountNameFull(Connection aConn) throws Exception {
		String result;
		if (Database.GetDbType(aConn) == DbType.MYSQL || Database.GetDbType(aConn) == DbType.ORACLE) {
			result = this.getTableName() + "." + this.createChildCountColName();
		} else {
			result = this.createChildCountColName();
		}
		return(result);
	}

	public String createChildCountColName() throws Exception {
		String result;
		result = this.tableName + POSTFIX_FIELD_CHILD_COUNT;
		return(result);
	}

	public void createFieldChildCount() throws Exception {
		this.createFieldChildCount(this.getMetaRec());
	}

	public void createFieldChildCount(Record aRec) throws Exception {
		FieldInt countField = (FieldInt) aRec.getField(this.createChildCountColName());
		if (countField == null) {
			countField = (FieldInt) aRec.createField(this.createChildCountColName(), FieldType.INTEGER);
			countField.setDefaultValue("0"); // in persistCommit we do childCount + 1, and if no default is NULL + 1
		}
		countField.setValueInt(0);
		countField.setModified(false);
		countField.setChildCount(true);
	}

	public String getIhTableName() {
		String result = GetIhTableName(this.getClass());
		return(result);
	}

	public static String GetIhTableName(Class<?> aClasz) {
		String ihName = Clasz.GetInheritancePrefix() + Database.Java2DbTableName(aClasz.getSimpleName()); // create the parent ih_ table
		return(ihName);
	}

	public String getIvTableName() {
		String result = GetIvTableName(this.getClass());
		return(result);
	}

	public static String GetIvTableName(Class<?> aChild) {
		String ivName = Clasz.GetIvPrefix() + Database.Java2DbTableName(aChild.getSimpleName()); // create the parent iv_ table
		return(ivName);
	}

	public static <Ty extends Clasz<?>> String GetIwTableName(FieldObjectBox<Ty> aFobMember) {
		Clasz<?> parentClasz = aFobMember.getMasterObject();
		return(Clasz.GetIwTableName(parentClasz.getClass(), aFobMember.getDbFieldName()));
	}

	public String getIwTableName(String aFieldName) {
		String result = Clasz.GetIwTableName(this.getClass(), aFieldName);
		return(result);
	}

	public static String GetIwTableName(Class<?> aParent, String aFieldName) {
		String iwName = Clasz.GetIwPrefix() + Database.Java2DbTableName(aParent.getSimpleName()) + "_" + Database.Java2DbTableName(aFieldName); // create the parent iv_ table
		return(iwName);
	}

	public Long getObjectId() throws Exception {
		return(this.getInstantRecord().getFieldLong(this.getPkName()).getValueLong());
	}

	public Long getObjectId(Class<?> aClass) throws Exception {
		if (this.getClass() == aClass) {
			return(this.getInstantRecord().getFieldLong(this.getPkName()).getValueLong());
		} else {
			return(this.getParentObjectByContext().getObjectId(aClass));
		}
	}

	public void setObjectId(String aObjectId) throws Exception {
		Long objectId = Long.parseLong(aObjectId);
		this.setObjectId(objectId, true); // set the clszObject id and do not state that he it as modify 
	}
	
	public void setObjectId(long objectId) throws Exception {
		this.setObjectId(objectId, true); // set the clszObject id and do not state that he it as modify 
	}

	public void setObjectId(Long objectId) throws Exception {
		this.setObjectId(objectId, true); // set the clszObject id and do not state that he it as modify 
	}

	public void setObjectId(long objectId, boolean aSetAsNotModify) throws Exception {
		this.getInstantRecord().getFieldLong(this.getPkName()).setValueLong(objectId);
		if (aSetAsNotModify) {
			this.getInstantRecord().getField(this.getPkName()).setModified(false); // changing primary key is not assume as modified
		}
	}

	public boolean isPopulated() throws Exception {
		if (this.getObjectId().equals(NOT_INITIALIZE_OBJECT_ID) == false) {
			return(true);
		} else {
			return(false);
		}
	}

	public void validateField(String aFieldName) throws Exception {
		if (this.getParentObjectByContext() == null) {
			throw new Hinderance("The class inheritance tree do not have the field: " + aFieldName);
		}
	}

	// TODO for each field type, need to do the Clasz get from parent if get fail
	public Boolean getValueBoolean(String aFieldName) throws Exception {
		if (this.fieldExist(aFieldName)) {
			return(this.getInstantRecord().getValueBoolean(aFieldName));
		} else {
			this.validateField(aFieldName);
			return(this.getParentObjectByContext().getValueBoolean(aFieldName));
		}
	}

	public String getValueStr(Connection aConn) throws Exception {
		return this.getValueStr();
	}

	public String getValueStr() throws Exception {
		throw new Hinderance("Missing method getValueStr in Clasz object: '" + this.getClass().getSimpleName() + "'");
	}

	public String getValueStr(String aFieldName) throws Exception {
		String result;
		if (this.fieldExist(aFieldName)) {
			result = (this.getInstantRecord().getValueStr(aFieldName));
			if (result == null) {
				result = "";
			}
		} else {
			this.validateField(aFieldName);
			result = (this.getParentObjectByContext().getValueStr(aFieldName));
		}
		return(result);
	}

	public Integer getValueInt(String aFieldName) throws Exception {
		Integer result;
		if (this.fieldExist(aFieldName)) {
			result = this.getInstantRecord().getValueInt(aFieldName);
		} else {
			this.validateField(aFieldName);
			result = (this.getParentObjectByContext().getValueInt(aFieldName));
		}
		return(result);
	}

	public Long getValueLong(String aFieldName) throws Exception {
		Long result;
		if (this.fieldExist(aFieldName)) {
			result = this.getInstantRecord().getValueLong(aFieldName);
		} else {
			this.validateField(aFieldName);
			result = (this.getParentObjectByContext().getValueLong(aFieldName));
		}
		return(result);
	}

	public Clasz<?> getNonNullObject(Connection aConn, String aFieldName) throws Exception {
		Clasz<?> result = this.getNullableObject(aConn, aFieldName);
		if (result == null) {
			throw new Hinderance("Fail to get value from field: '" + aFieldName + "', in object: " + this.getClaszName() + "'");
		}
		return result;
	}

	public Clasz<?> getNullableObject(Connection aConn, String aFieldName) throws Exception {
		Clasz<?> result;
		if (this.fieldExist(aFieldName)) {
			result = this.getInstantRecord().getValueObject(aConn, aFieldName);
		} else {
			this.validateField(aFieldName);
			result = this.getParentObjectByContext().getNullableObject(aConn, aFieldName);
		}
		return result;
	}

	public Clasz<?> getNullableObject(String aFieldName) throws Exception { // fetch from memory, not involving db, no connection parameter
		Clasz<?> result;
		if (this.fieldExist(aFieldName)) {
			result = this.getInstantRecord().getValueObject(aFieldName);
		} else {
			this.validateField(aFieldName);
			result = this.getParentObjectByContext().getNullableObject(aFieldName);
		}
		return result;
	}

	public DateTime getValueDateTime(String aFieldName) throws Exception {
		DateTime result;
		if (this.fieldExist(aFieldName)) {
			result = (this.getInstantRecord().getValueDateTime(aFieldName));
		} else {
			this.validateField(aFieldName);
			result = (this.getParentObjectByContext().getValueDateTime(aFieldName));
		}
		return(result);
	}

	public DateTime getValueDate(String aFieldName) throws Exception {
		DateTime result;
		if (this.fieldExist(aFieldName)) {
			result = (this.getInstantRecord().getValueDate(aFieldName));
		} else {
			this.validateField(aFieldName);
			result = (this.getParentObjectByContext().getValueDate(aFieldName));
		}
		return(result);
	}

	public void setValueStr(String aValue) throws Exception {
		throw new Hinderance("The clasz: " + this.getClaszName() + ", have not implemented setValueStr method");
	}

	public void setValueStr(Connection aConn, String aValue) throws Exception {
		this.setValueStr(aValue);
	}

	public void setValueStr(String aFieldName, String aFieldValue) throws Exception {
		if (this.fieldExist(aFieldName)) {
			this.getInstantRecord().setValueStr(aFieldName, aFieldValue);
		} else {
			this.validateField(aFieldName);
			this.getParentObjectByContext().setValueStr(aFieldName, aFieldValue);
		}
	}

	public void setValueLong(String aFieldName, Long aFieldValue) throws Exception {
		if (this.fieldExist(aFieldName)) {
			this.getInstantRecord().setValueLong(aFieldName, aFieldValue);
		} else {
			this.validateField(aFieldName);
			this.getParentObjectByContext().setValueLong(aFieldName, aFieldValue);
		}
	}

	public void setValueInt(String aFieldName, Integer aFieldValue) throws Exception {
		if (this.fieldExist(aFieldName)) {
			this.getInstantRecord().setValueInt(aFieldName, aFieldValue);
		} else {
			this.validateField(aFieldName);
			this.getParentObjectByContext().setValueInt(aFieldName, aFieldValue);
		}
	}

	public void setValueBoolean(String aFieldName, boolean aFieldValue) throws Exception {
		if (this.fieldExist(aFieldName)) {
			this.getInstantRecord().setValueBoolean(aFieldName, aFieldValue);
		} else {
			this.validateField(aFieldName);
			this.getParentObjectByContext().setValueBoolean(aFieldName, aFieldValue);
		}
	}

	public void setValueDateTime(String aFieldName, DateTime aFieldValue) throws Exception {
		if (this.fieldExist(aFieldName)) {
			this.getInstantRecord().setValueDateTime(aFieldName, aFieldValue);
		} else {
			this.validateField(aFieldName);
			this.getParentObjectByContext().setValueDateTime(aFieldName, aFieldValue);
		}
	}

	public void setValueDate(String aFieldName, DateTime aFieldValue) throws Exception {
		if (this.fieldExist(aFieldName)) {
			this.getInstantRecord().setValueDate(aFieldName, aFieldValue);
		} else {
			this.validateField(aFieldName);
			this.getParentObjectByContext().setValueDate(aFieldName, aFieldValue);
		}
	}

	private void setValueObject(String aFieldName, Clasz<?> aFieldValue) throws Exception {
		if (this.fieldExist(aFieldName)) {
			FieldObject<?> oneField = (FieldObject<?>) this.getField(aFieldName);
			oneField.setValueObjectFreeType(aFieldValue);
		} else {
			this.validateField(aFieldName);
			this.getParentObjectByContext().setValueObject(aFieldName, aFieldValue);
			if (aFieldValue != null) aFieldValue.setMasterObject(this);
		}
	}

	public static <Tf extends Clasz<?>> void SetValueObject(Class<Tf> aClass, Clasz<?> aThis, String aFieldName, Tf aFieldValue) throws Exception {
		aThis.setValueObject(aFieldName, aClass.cast(aFieldValue));
	}

	private void addValueObject(Connection aConn, String aFieldName, Clasz<?> aFieldValue) throws Exception {
		if (this.fieldExist(aFieldName)) {
			FieldObjectBox<?> fob2AddObj = (FieldObjectBox<?>) this.getField(aFieldName);
			if (fob2AddObj.getMetaObj().getClaszName().equals("Clasz")) {
				Clasz<?> metaObj = ObjectBase.CreateObject(aConn, aFieldValue.getClass());
				fob2AddObj.setMetaObj(metaObj);
			}
			fob2AddObj.addValueObjectFreeType(aFieldValue);
		} else {
			this.validateField(aFieldName);
			this.getParentObjectByContext().addValueObject(aConn, aFieldName, aFieldValue);
			if (aFieldValue != null) aFieldValue.setMasterObject(this);
		}
	}

	/* see AddValueObject, this one still give warning: unchecked conversion
	@SuppressWarnings("unchecked")
	public <Tf extends Clasz> void addValueObject(Connection aConn, Class<Tf> aClass, String aFieldName, Tf aFieldValue) throws Exception {
		this.addValueObject(aConn, aFieldName, aClass.cast(aFieldValue));
	}
	*/

	public static <Tf extends Clasz<?>> void AddValueObject(Connection aConn, Class<Tf> aClass, Clasz<?> aThis, String aFieldName, Tf aFieldValue) throws Exception {
		aThis.addValueObject(aConn, aFieldName, aClass.cast(aFieldValue));
	}

	public void setObjectId(Connection aConn) throws Exception {
		long oid = this.getNextObjectId(aConn);
		this.setObjectId(oid, true);
	}

	public long getNextObjectId(Connection aConn) throws Exception {
		long result = Database.GetNextSequence(aConn, this.createSequenceTableName());
		return(result);
	}

	/**
	 * When inserting clszObject, the primary key needs to be handle differently
	 * according to the database type, for mysql database, the primary key MUST be
	 * zero value so that its automatically generated by the database. For oracle,
	 * the primary key value must be extracted from a sequence
	 *
	 * 
	 * @param aConn
	 * @throws Exception 
	 */
	public void insert(Connection aConn) throws Exception {
		if (this.getObjectId().equals(NOT_INITIALIZE_OBJECT_ID) == false) {
			throw new Hinderance("Cannot insert an object that already have primary key assigned to it");
		}
		this.setObjectId(aConn); // create and place in the objectId/primarykey into this clszObject before the insertion
		this.insert(aConn, Clasz.INSTANT_RECORD_AT);
	}

	public void update(Connection aConn) throws Exception {
		Record rec2Update = this.getInstantRecord();

		Record recWhere = new Record();
		recWhere.createField(aConn, rec2Update.getField(rec2Update.getPkFieldName()));
		recWhere.copyValue(aConn, rec2Update.getField(rec2Update.getPkFieldName()));

		this.update(aConn, rec2Update, recWhere);
	}

	public void updateIndex(Connection aConn) throws Exception {
		ObjectIndex.UpdateIndexAll(aConn, this);
	}

	public void deleteIndex(Connection aConn) throws Exception {
		ObjectIndex.DeleteIndex(aConn, this);
	}

	public boolean removeField(String aFieldName) throws Exception {
		boolean result;
		if (aFieldName == null) {
			result = true;
		} else if (this.fieldExist(aFieldName)) {
			result = this.getInstantRecord().removeField(aFieldName);
			this.gotDeletedField = true;
		} else {
			this.validateField(aFieldName);
			result = this.getParentObjectByContext().removeField(aFieldName);
		}
		return(result);
	}

	public void removeMarkField() throws Exception {
		for(Field eachField : this.getTreeField().values()) {
			if (eachField.isSystemField() == false) {
				if (eachField.forRemove()) {
					this.removeField(eachField.getDbFieldName());
				}
			}
		}
	}

	public void markAllFieldForRemoval() throws Exception {
		for(Field eachField : this.getTreeField().values()) {
			if (eachField.isSystemField() == false) {
				eachField.forRemove(true);
			}
		}
	}

	public void markAllFieldForKeep() throws Exception {
		for(Field eachField : this.getTreeField().values()) {
			if (eachField.isSystemField() == false) {
				eachField.forRemove(false);
			}
		}
	}

	public boolean gotFieldDeleted() throws Exception {
		boolean result = false;
		if (this.gotDeletedField == false) {
			if (this.getParentObjectByContext() != null) result = this.getParentObjectByContext().gotFieldDeleted();
		} else {
			result = true;
		}
		return(result);
	}

	public boolean gotFieldCreated() throws Exception {
		boolean result = false;
		if (this.gotCreatedField == false) {
			if (this.getParentObjectByContext() != null) result = this.getParentObjectByContext().gotFieldCreated();
		} else {
			result = true;
		}
		return(result);
	}

	// TODO when copying table field type, do a deep copy since by default java uses shallow copy (i.e. copying by reference)
	public void copyAllFieldWithModifiedState(Connection aConn, Clasz<?> aSource) throws Exception {
		this.copyClasz(aConn, aSource, false); // clonse fields will keep the modify status of the original fields
	}

	public void copyAllFieldWithoutModifiedState(Connection aConn, Clasz<?> aSource) throws Exception {
		this.copyClasz(aConn, aSource, true);
	}

	private void copyClasz(Connection aConn, Clasz<?> aSource, boolean aIsCopyWithoutModifiedState) throws Exception {
		for(Field eachField : aSource.getInstantRecord().getFieldBox().values()) {
			try {
				if (eachField.getDbFieldType() == FieldType.OBJECT) {
					Clasz<?> memberObj = ((FieldObject<?>) eachField).getObj();
					if (memberObj != null && memberObj.getClaszName().equals("Clasz")) {
						continue;
					}
				}
				if (eachField.getDbFieldType() == FieldType.OBJECTBOX) {
					Clasz<?> memberObj = ((FieldObjectBox<?>) eachField).getMetaObj();
					if (memberObj.getClaszName().equals("Clasz")) {
						continue;
					}
				}
				if (aIsCopyWithoutModifiedState) {
					this.getInstantRecord().copyValue(aConn, eachField); // for clszObject types, there's a overidden method to enable each clszObject type knows how to copy among themselves
				} else {
					this.getInstantRecord().cloneField(aConn, eachField);
				}
			} catch (Exception ex) {
				throw new Hinderance(ex, "Error when copying object : '" + this.getClass().getSimpleName() + "', field: '" + eachField.getDbFieldName() + "'");
			}
		}

		if (ObjectBase.ParentIsNotAtClaszYet(this)) {
			Clasz<?> localParent = this.getParentObjectByContext();
			Clasz<?> parentSource = aSource.getParentObjectByContext();
			if (localParent != null) {// its possible the parent class for this clszObject is abstract and still not at Clasz
				localParent.copyClasz(aConn, parentSource, aIsCopyWithoutModifiedState);
			}
		}
	}

	/**
	 * TODO separate out each field type to reduce memory consumption
	 *
	 * @return
	 * @throws Exception
	 */
	public String asString() throws Exception {
		return(this.asString(false));
	}

	public String asString(boolean aDisplayMember) throws Exception {
		String result = "";
		for(Field eachField : this.getInstantRecord().getFieldBox().values()) {
			if (aDisplayMember && eachField.getDbFieldType() == FieldType.OBJECT) {
				result += ((FieldObject<?>) eachField).getObj().asString(aDisplayMember);
			} else if (aDisplayMember && eachField.getDbFieldType() == FieldType.OBJECTBOX) {
				throw new Hinderance("Field of OBJECTBOX is not supported yet!!");
			} else {
				result += eachField.getDbFieldName() + ": " + eachField.getValueStr() + App.LineFeed;
			}
		}
		if (ObjectBase.ParentIsNotAtClaszYet(this)) {
			Clasz<?> parntObject = this.getParentObjectByContext();
			if (parntObject != null) { // its possible the parent class for this clszObject is abstract and still not at Clasz
				result += parntObject.asString();
			}
		}
		return(result);
	}

	public Clasz<?> getChildObject(Connection aConn, Clasz<?> aLeafObject) throws Exception {
		Clasz<?> result;
		Clasz<?> childObject = this.getChildClasz(aLeafObject);
		if (childObject != null) {
			String ihName = childObject.getIhTableName();

			Record recSelect = new Record();
			recSelect.createField(Clasz.CreatePkColName(childObject.getClass()), FieldType.LONG); // select the primary key of the child clszObject

			Record recWhere = new Record();
			recWhere.createField(this.getPkName(), FieldType.LONG);
			recWhere.getFieldLong(this.getPkName()).setValueLong(this.getObjectId());

			Table ihTable = new Table(ihName);
			if (ihTable.fetch(aConn, recSelect, recWhere) != 1) {// Fetch, result is in recSelect
				//ignore, it doesn't have a child clszObject in the database
			} else {
				// Fetch the clszObject from the database
				if (FetchUnique(aConn, childObject, recSelect) != 1) { // Fetch, result is in recSelect
					throw new Hinderance("Fail to retrieve object for: '" + childObject.getClass().getSimpleName() + "', from its field: " + recSelect.toStr());
				}
			}

			result = childObject;
		} else {
			result = null;
		}
		return(result);
	}

	public static int FetchUnique(Connection aConn, Clasz<?> aClasz, Record aWhere) throws Exception {
		int result = 0;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			rs = Table.Fetch(aConn, stmt, aClasz.getTableName(), null, aWhere);
			while (rs.next()) {
				if (result > 0) {
					throw new Hinderance("Object to fetch criteria returns more then one record: '" + aClasz.getClass().getSimpleName() + "'");
				}
				aClasz.populateObject(aConn, rs, false);
				result++;
			}
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (stmt != null) {
				stmt.close();
			}
		}
		return(result);
	}

	public static void BindSqlDateTimeOrDate(PreparedStatement aStmt, Field aKeyField, String aKeyValue, int aPosition) throws Exception {
		java.sql.Timestamp dateValue;
		Field fieldDt = (Field) aKeyField;
		fieldDt.setValueStr(aKeyValue);
		String dbDate;
		if (fieldDt instanceof FieldDateTime) {
			dbDate = DateAndTime.FormatForJdbcTimestamp(((FieldDateTime) fieldDt).getValueDateTime());
		} else { // this is instance of FieldDate with no time
			FieldDate castedDate = (FieldDate) fieldDt;
			DateTime dateNoTime = castedDate.getValueDate();
			DateTime endOfDay = DateAndTime.GetDayStart(dateNoTime);
			dbDate = DateAndTime.FormatForJdbcTimestamp(endOfDay);
		}
		dateValue = java.sql.Timestamp.valueOf(dbDate); // format must be in "2005-04-06 09:01:10"
		aStmt.setTimestamp(aPosition, dateValue);
	}

	public static String SetOrderBy(String aAccumStr, String aFieldName, SortOrder aOrderBy) {
		aAccumStr = aAccumStr.trim();
		if (aAccumStr.isEmpty() == false) aAccumStr += ",";
		String strOrder = "asc";
		if (aOrderBy.equals(SortOrder.DSC)) strOrder = "desc";
		aAccumStr += " " + aFieldName + " " + strOrder;

		return(aAccumStr.trim());
	}

	public static <Ty extends Clasz<?>> Ty FetchUnique(Connection aConn, Ty aClasz, Record aWhere, String aSortFieldName, SortOrder aDisplayOrder) throws Exception {
		List<String> keyField = new CopyOnWriteArrayList<>();
		List<String> keyValue = new CopyOnWriteArrayList<>();
		String colName =  Database.Java2DbFieldName(aSortFieldName);
		keyField.add(colName);
		keyValue.add("");
		return FetchUnique(aConn, aClasz, keyField, keyValue, aWhere, aDisplayOrder);
	}

	@SuppressWarnings("unchecked")
	private static <Ty extends Clasz<?>> Ty FetchUnique(Connection aConn, Ty aClasz, List<String> aKeyField, List<String> aKeyValue, Record aWhere, SortOrder aSortOrder) throws Exception {
		FieldObjectBox<Ty> fobResult = new FieldObjectBox<Ty>(aClasz);
		Multimap<String, Record> whereBox = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
		String tableName = CreateTableName(aClasz);
		whereBox.put(tableName, aWhere);
		List<SortOrder> orderList = new CopyOnWriteArrayList<>();
		orderList.add(aSortOrder);
		FetchStatus status = FetchBySection(aConn, (Class<Ty>) aClasz.getClass(), aKeyField, aKeyValue, orderList, whereBox, fobResult, PageDirection.AsString(PageDirection.NEXT), 1, null);
		fobResult.setFetchStatus(status);
		fobResult.resetIterator();
		if (fobResult.hasNext(aConn)) {
			Ty result = fobResult.getNext();
			return(result);
		} 
		return(null);
	}

	/*
	public void ForEachClasz(Connection aConn, Record aWhere, Callback2ProcessClasz<Ty> aCallback) throws Exception {
		ForEachClasz(aConn, this.getClass(), aWhere, aCallback);
	}
	*/

	public static <Ty extends Clasz<?>> void ForEachClasz(Connection aConn, Class<Ty> aClaszClass, Record aWhere, Callback2ProcessMember<Ty> aCallback) throws Exception {
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			String tableName = CreateTableName(aClaszClass);
			rs = Table.Fetch(aConn, stmt, tableName, null, aWhere);
			while (rs.next()) {
				Ty clasz = ObjectBase.CreateObject(aConn, aClaszClass);
				clasz.populateObject(aConn, rs, false);
				if (aCallback != null) {
					if (aCallback.processClasz(aConn, clasz) == false) {
						break;
					}
				}
			}
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (stmt != null) {
				stmt.close();
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static <Ty extends Clasz<?>> int FetchAll2Box(Connection aConn, Record aWhere, FieldObjectBox<Ty> aBox) throws Exception {
		int result = 0;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			Ty typeClasz = aBox.getMetaObj();
			rs = Table.Fetch(aConn, stmt, typeClasz.getTableName(), null, aWhere);
			while (rs.next()) {
				Ty clasz = ObjectBase.CreateObject(aConn, (Class<Ty>) typeClasz.getClass());
				clasz.populateObject(aConn, rs, false);
				aBox.addValueObject(clasz);
				result++;
			}
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (stmt != null) {
				stmt.close();
			}
		}
		return(result);
	}

	/**
	 * Traverse upwards from the leaf clszObject to get the immediate child class of
	 * 'this' clszObject. Since there can be multiple child class for a parent class,
	 * the leaf class is use to guide the traversing to the leaf class path.
	 * Return null if there is no child class or if the class is already at the
	 * leaf class.
	 *
	 * @param aLeafClass
	 * @return
	 * @throws Exception if the leaf class is not in the inheritance tree of 'this'
	 */
	public Clasz<?> getChildClasz(Clasz<?> aLeafClass) throws Exception {
		if (aLeafClass.getClass().equals(this.getClass())) {
			return(null);
		} else {
			return(this.getChildClaszRecursively(aLeafClass));
		}
	}

	public Class<?> getChildClass(Class<?> aLeafClass) throws Exception {
		if (aLeafClass.equals(this.getClass())) {
			return(null);
		} else {
			return(this.getChildClassRecursively(aLeafClass));
		}
	}

	private Clasz<?> getChildClaszRecursively(Clasz<?> aLeafClass) throws Exception {
		Clasz<?> result = aLeafClass;
		Clasz<?> parentClass = aLeafClass.getParentObjectByContext();
		try {
			if (parentClass == null) {
				throw new Hinderance("This class: '" + aLeafClass.getClass().getSimpleName() + "', do not have inheritance from the class: " + this.getClass().getSimpleName() + "'");
			} else if (parentClass.getClass().equals(this.getClass()) == false) { // if the parent of this class is the same as the class of 'this' clszObject, then this class is the child class
				result = this.getChildClaszRecursively(parentClass); // recursive traversing up the tree to get the immediate child class of this clszObject
			}
		} catch (Exception ex) {
			if (parentClass != null)
				throw new Hinderance(ex, "Fail to get child class for: '" + parentClass.getClass().getSimpleName() + "', for class: '" + this.getClass().getSimpleName() + "'");
			else
				throw new Hinderance(ex, "Fail to get child class for class: '" + this.getClass().getSimpleName() + "'");
		}

		return(result);
	}

	private Class<?> getChildClassRecursively(Class<?> aLeafClass) throws Exception {
		Class<?> result = aLeafClass;
		Class<?> parentClass = aLeafClass.getSuperclass();
		try {
			if (parentClass == null) {
				throw new Hinderance("This class: '" + aLeafClass.getSimpleName() + "', do not have inheritance from the class: '" + this.getClass().getSimpleName() + "'");
			} else if (parentClass.equals(this.getClass()) == false) { // if the parent of this class is the same as the class of 'this' clszObject, then this class is the child class
				result = this.getChildClassRecursively(parentClass); // recursive traversing up the tree to get the immediate child class of this clszObject
			}
		} catch (Exception ex) {
			if (parentClass != null) {
				throw new Hinderance(ex, "Fail to get child class for: '" + parentClass.getSimpleName() + "', this class: '" + this.getClass().getSimpleName() + "'");
			} else {
				throw new Hinderance(ex, "Fail to get child class for null" + ", this class: '" + this.getClass().getSimpleName() + "'");
			}
		}

		return(result);
	}

	public void copyShallow(Clasz<?> aSource) throws Exception {
		for(Field eachField : aSource.getInstantRecord().getFieldBox().values()) {
			this.copyField(eachField);
		}
	}

	public void copyField(Field eachField) throws Exception {
		copyField(eachField, this);
	}

	public static void copyField(Field eachField, Clasz<?> aObject) throws Exception {
		//App.logDebg("Shallow copying field: " + reflectField.getFieldName() + ", value: " + reflectField.getValueStr());
		if (eachField.getDbFieldType() == FieldType.STRING) {
			aObject.setValueStr(eachField.getDbFieldName(), eachField.getValueStr());
		} else if (eachField.getDbFieldType() == FieldType.DATETIME) {
			aObject.setValueDateTime(eachField.getDbFieldName(), ((FieldDateTime) eachField).getValueDateTime());
		} else if (eachField.getDbFieldType() == FieldType.DATE) {
			aObject.setValueDate(eachField.getDbFieldName(), ((FieldDate) eachField).getValueDate());
		} else if (eachField.getDbFieldType() == FieldType.LONG) {
			aObject.setValueLong(eachField.getDbFieldName(), ((FieldLong) eachField).getValueLong());
		} else if (eachField.getDbFieldType() == FieldType.INTEGER) {
			aObject.setValueInt(eachField.getDbFieldName(), ((FieldInt) eachField).getValueInt());
		} else if (eachField.getDbFieldType() == FieldType.BOOLEAN) {
			aObject.setValueBoolean(eachField.getDbFieldName(), ((FieldBoolean) eachField).getValueBoolean());
		} else if (eachField.getDbFieldType() == FieldType.ENCRYPT) {
			aObject.setValueStr(eachField.getDbFieldName(), eachField.getValueStr());
		} else {
			throw new Hinderance("Unknown type for field: '" + eachField.getDbFieldName() + "', when attempting to copy it from: '" + aObject.getClass().getSimpleName() + "'");
		}
	}

	public void populateObject(Connection aConn, ResultSet aRset) throws Exception {
		this.populateObject(aConn, aRset, this, true, true);
	}

	public void populateObject(Connection aConn, ResultSet aRset, boolean aPopulateInheritance) throws Exception {
		this.populateObject(aConn, aRset, this, aPopulateInheritance, true);
	}

	/**
	* Populate member variable fields (either one instant variable or multiple
	* instant variable of aMasterObject).
	*
	* @param aConn
	* @param aRset
	* @param aObject
	* @param aPopulateInheritance
	* @param aPopulateMember
	* @throws Exception 
	*/
	public void populateObject(Connection aConn, ResultSet aRset, Clasz<?> aObject, boolean aPopulateInheritance, boolean aPopulateMember) throws Exception {
		try {
			// must first populate the master clszObject before populating member fields because the objectid is needed when populating member fields
			for(Field eachField : aObject.getInstantRecord().getFieldBox().values()) {
				if (eachField.getDbFieldType() == FieldType.OBJECT || eachField.getDbFieldType() == FieldType.OBJECTBOX) {
				} else {
					try {
						eachField.setMasterObject(aObject);
						eachField.populateField(aRset);	 // retrieve the field from result set according to the field's name and place the value into the field
					} catch (Exception ex) {
						throw new Hinderance(ex, "Fail to populate from database into: '" + aObject.getClass().getSimpleName() + "." + eachField.getCamelCaseName() + "'");
					}
				}
			}

			// now for the member fields
			for(Field eachField : aObject.getInstantRecord().getFieldBox().values()) {
				try {
					if (eachField.getDbFieldType() == FieldType.OBJECT || eachField.getDbFieldType() == FieldType.OBJECTBOX) {
						if (((FieldClasz) eachField).isPrefetch() == true) {
							if (aPopulateMember) {
								if (eachField.isInline() == false) {
									if (eachField.getDbFieldType() == FieldType.OBJECT) {
											((FieldObject<?>) eachField).fetch(aConn); // Fetch the clszObject into this field
											eachField.setMasterObject(aObject); // after fetch, it's a different master during create
											// TODO for OBJECT type, fetchObjectFast result set would have had the value, so the above sql is not needed if performance is needed 
									} else if (eachField.getDbFieldType() == FieldType.OBJECTBOX) {
										if (((FieldObjectBox<?>) eachField).getMetaObj().getClaszName().equals("Clasz") == false) { // if only this is not a recursive field, then we populate it
											((FieldObjectBox<?>) eachField).fetchAll(aConn);
										}
										eachField.setMasterObject(aObject); // after fetch, it's a different master during create
									} else {
										throw new Hinderance("Field is not either OBJECT or OBJECTBOX type, internal error when processing field: '" + eachField.getDbFieldName() + "'");
									}
								} else { // inline field, copy the value from the result set into the member clszObject
									eachField.setMasterObject(aObject); // after fetch, it's a different master during create
									resultset2Tree(aConn, aRset, (FieldObject<?>) eachField, eachField.getDbFieldName());
								}
							} else {
								eachField.setMasterObject(aObject); // after fetch, it's a different master during create
							}
						} else {
							eachField.setMasterObject(aObject); // after fetch, it's a different master during create
						}
					}
				} catch (Exception ex) {
					throw new Hinderance(ex, "Fail to populate field: '" + aObject.getClass().getSimpleName() + "." + eachField.getCamelCaseName() + "'");
				}
			}
		} catch (Exception ex) {
			throw new Hinderance(ex, "Fail to populate result set values to object: '" + aObject.getClass().getSimpleName() + "'");
		}

	}

	/**
	 * Populate values from the result set and place them into the member object
	 * by retrieving the field from result set according to the field's name and
	 * place the value into the member object field.
	 *
	 * @param aDb
	 * @param aConn
	 * @param aRset
	 * @param aMasterField
	 * @param aFieldName
	 * @throws Exception 
	 */

	/**
	 * Populate values from the result set and place them into the member clszObject
	 * by retrieving the field from result set according to the field's name and
	 * place the value into the member clszObject field.
	 * @param aDb
	 * @param aConn
	 * @param aRset
	 * @param aMasterField
	 * @param aFieldName
	 * @throws Exception
	 */
	public static void resultset2Tree(Connection aConn, ResultSet aRset, FieldObject<?> aMasterField, String aFieldName) throws Exception {
		try {
			FieldObject<?> fieldObj = aMasterField;
			Clasz<?> memberObj = fieldObj.getObj();
			if (memberObj == null) {
				fieldObj.createNewObject(aConn);
				memberObj = fieldObj.getObj();
			}

			for(Field eachField : memberObj.getInstantRecord().getFieldBox().values()) {
				eachField.setMasterObject(memberObj); // after fetch, it's a different master during create
				if (eachField.getDbFieldType() == FieldType.OBJECT) {
					String dbFieldName = Clasz.CreateDbFieldName(eachField.getDbFieldName(), aFieldName); // create the field name of the inline field from its member name and its field name
					resultset2Tree(aConn, aRset, (FieldObject<?>) eachField, dbFieldName);
				} else if (eachField.getDbFieldType() == FieldType.OBJECTBOX) { 
					// TODO handle objectbox type
				} else {
					if (eachField.isSystemField() == false) {
						Field tmpField = Field.CreateField(eachField);
						//String dbFieldName = Clasz.CreateDbFieldName(reflectField.getFieldName(), aMasterField.getFieldName()); // create the field name of the inline field from its member name and its field name
						String dbFieldName = Clasz.CreateDbFieldName(eachField.getDbFieldName(), aFieldName); // create the field name of the inline field from its member name and its field name
						tmpField.setDbFieldName(dbFieldName);
						tmpField.populateField(aRset);
						eachField.copyValue(aConn, tmpField);
					}
				}
			}
		} catch (Exception ex) {
			throw new Hinderance(ex, "Fail at resultset2Tree for field: '" + aMasterField.getDbFieldName() + "'");
		}
	}

	public static <Ty extends Clasz<?>> void PopulateInlineField(Connection aConn, Clasz<?> aMasterClasz, FieldObject<Ty> aInlineField, String aAccumFieldName, String aDirection) throws Exception {
		Ty memberObj = aInlineField.getObj();
		if (memberObj != null) {
			for(Field eachField : memberObj.getInstantRecord().getFieldBox().values()) {
				if (eachField.getDbFieldType() == FieldType.OBJECT) { 
					String dbFieldName = Clasz.CreateDbFieldName(eachField.getDbFieldName(), aAccumFieldName); // create the field name of the inline field from its member name and its field name
					PopulateInlineField(aConn, aMasterClasz, (FieldObject<?>) eachField, dbFieldName, aDirection);
				} else if (eachField.getDbFieldType() == FieldType.OBJECTBOX) { // TODO handle objectbox type
					throw new Hinderance("Inline field for FieldObjectBox is not implemented yet");
				} else {
					if (eachField.isSystemField() == false) {
						String inlineFieldName = Clasz.CreateDbFieldName(eachField.getDbFieldName(), aAccumFieldName);
						Field fieldRec = aMasterClasz.getField(inlineFieldName);
						if (aDirection.equals("tree")) {
							eachField.copyValue(aConn, fieldRec);
						} else if (aDirection.equals("flat")) {
							fieldRec.copyValue(aConn, eachField);
						} else {
							throw new Hinderance("Attempting to copy inline fields to object or vice versa, invalid direction: " + aDirection);
						}
					}
				}
			}
		}
	}

	public static Clasz<?> CreateObjectFromAnyClass(Connection aConn, Class<?> aClass) throws Exception {
		return CreateObject(aConn, aClass.asSubclass(Clasz.class));
	}

	public static <Ty extends Clasz<?>> Ty CreateObject(Connection aConn, Class<Ty> aClass) throws Exception {
		Ty result = Clasz.CreateObject(aConn, aClass, true);
		return result;
	}

	public static <Ty extends Clasz<?>> Ty CreateObjectTransient(Connection aConn, Class<Ty> aClass) throws Exception {
		Ty result = Clasz.CreateObject(aConn, aClass, false);
		return result;
	}

	public static Clasz<?> CreateObjectTransientFromAnyClass(Connection aConn, Class<?> aClass) throws Exception {
		Clasz<?> result = Clasz.CreateObject(aConn, aClass.asSubclass(Clasz.class), false);
		return result;
	}

	public static <Ty extends Clasz<?>> Ty CreateObject(Connection aConn, Class<Ty> aClass, boolean aDoDDL) throws Exception {
		CopyOnWriteArrayList<ClassAndField> avoidRecursive = new CopyOnWriteArrayList<>();
		Ty result = Clasz.CreateObject(aConn, aClass, null, aDoDDL, avoidRecursive, 0, false);
		return result;
	}

	/**
	 * Creates an clszObject from the given Class, the created clszObject will contain the
	 * appropriate parent clszObject if it has a inherited class. The method traverses
	 * up the inheritance tree until the Clasz class to create and assign its
	 * parent clszObject.
	 * 
	 * Handle abstract classes by combining the abstract class into the lowest
	 * normal child class and creates those abstract class fields inside a normal
	 * class.
	 *
	 * @param aDb
	 * @param aConn
	 * @param aClass
	 * @param aInstant - the previously instantiated clszObject for this class (use when contain abstract class)
	 * @param aDoDdl
	 * @param aDepth
	 * @param aAvoidRecursive
	 * @param aInline
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private static <Ty extends Clasz<?>> Ty CreateObject(Connection aConn, Class<?> aClass, Ty aInstant, boolean aDoDdl, List<ClassAndField> aAvoidRecursive, int aDepth, boolean aInline) throws Exception {
		ObjectBase objectDb = aConn.getObjectDb();
		Ty object = null;
		if (Modifier.isAbstract(aClass.getModifiers())) { // abstract class
			if (aDepth == 0) {
				throw new Hinderance("Cannot create object of type abstract!");
			} else {
				AlterObjectForAbstract(aConn, aClass, aInstant, aAvoidRecursive, aDepth, aDoDdl); // add the abstract class fields into the child table by using SQL alter table..
			}
		} else {
			object = CreateObjectDdl(aConn, (Class<Ty>) aClass, aDoDdl, aAvoidRecursive, aDepth, aInline); // create the table for this class in this db and also return the created instant of aClass 
			object.setObjectId(Clasz.NOT_INITIALIZE_OBJECT_ID, true);
			object.setDb(objectDb);
			aInstant = object;
		}

		Class<?> ParentClass = aClass.getSuperclass(); // get the parent clszObject of the class to be created

		if (ParentClass != null && ParentClass != Clasz.class) {
			if (ParentClass.isInterface()) {
				// not interested in interface class
			} else if (Modifier.isAbstract(ParentClass.getModifiers())) { // if the parent is an abstract class
				Clasz.CreateObject(aConn, ParentClass, aInstant, aDoDdl, aAvoidRecursive, aDepth + 1, aInline); // recursive call to check for the parent class if is still abstract class, do the alter else, do the normal create 
			} else {
				if (Modifier.isAbstract(aClass.getModifiers())) {  // if the parent of aClass is NOT abstract and aClass is abstract, means we need to create table for the non abstract parent class 
					Clasz<?> localParent = Clasz.CreateObject(aConn, ParentClass, aInstant, aDoDdl, aAvoidRecursive, aDepth + 1, aInline); // recursive call to create the non abstract parent class table 
					ObjectBase.createInheritance(aConn, ParentClass, aInstant.getClass()); // after creating the parent, create the inheritance tables, note the aInstant.getClass() parameter
					aInstant.setParentObject(localParent); // link the inheritance to a non abstract class
				} else {
					Clasz<?> parntObject = Clasz.CreateObject(aConn, ParentClass, aInstant, aDoDdl, aAvoidRecursive, aDepth + 1, aInline); // recursive call to create the parent table of the inherited parent class
					ObjectBase.createInheritance(aConn, ParentClass, aClass); // after creating the parent, create the inheritance tables
					if (object != null) {
						object.setParentObject(parntObject);
					} else {
						throw new Hinderance("Fail to create the object for DDL creation: " + aClass.getSimpleName());
					}
				}
			}
		}
		return(object);
	}

	/**
	 * Translate the class into a table and create it inside the database, it will
	 * also populate the db field name into the static variable declare in the
	 * class with the annotation ReflectField. The aClass argument is use to
	 * create an instant of an Clasz/Table that is then use to create the table in
	 * the database.
	 * 
	 * NOTE: The field name in the clszObject MUST be isUnique throughout the
	 * inheritance tree. The code get overly complex if those fields name are not
	 * isUnique throughout the inheritance tree.
	 *
	 *
	 * @param aConn
	 * @param aDoDdl
	 * @param aAvoidRecursive
	 * @param aDepth
	 * @param aClass
	 * @param aInline
	 * @return
	 * @throws Exception
	 */
	private static <Ty extends Clasz<?>> Ty CreateObjectDdl(Connection aConn, Class<Ty> aClass, boolean aDoDdl, List<ClassAndField> aAvoidRecursive, int aDepth, boolean aInline) throws Exception {
		//Ty clszObject = aClass.newInstance();
		Ty clszObject = aClass.getConstructor().newInstance();
		clszObject.createFieldPk(); // create field in meta record
		clszObject.createFieldChildCount(); // this field tracks the number of other child clszObject inherted from this clszObject
		clszObject.createClaszRecord(aConn); // with meta record fields created, create a data record
		CreateFieldFromClass(aConn, aClass, clszObject, "", aInline, aAvoidRecursive, aDepth, aDoDdl);

		if (aDoDdl && Database.TableExist(aConn, clszObject.getTableName()) == false) {
			App.logInfo(Clasz.class, "Creating table for class: '" + clszObject.getClass().getSimpleName() + "'");
			Database.CreateTable(aConn, clszObject);
			Database.CreatePrimaryKey(aConn, clszObject); // do alter table to create the primary key
			Database.CreateSequence(aConn, clszObject.createSequenceTableName()); // alter the table to make the primary key auto increment
			Database.CreateIndexes(aConn, clszObject.getTableName(), clszObject.getInstantRecord());
		} else if (aDoDdl) {
			// Table already exists: ensure any ignoreCase generated _lc columns are present.
			Database.AlterTableAddIgnoreCaseColumns(aConn, clszObject);
		}

		return(clszObject);
	}

	/**
	 * Creates the data record for this clasz.
	 *
	 * @return
	 * @throws Exception
	 */
	public Record createClaszRecord(Connection aConn) throws Exception {
		Record dataRec = new Record();
		dataRec.createField(aConn, this.getMetaRec()); // create the structure of the meta rec to dataRec
		this.getRecordBox().put(Clasz.INSTANT_RECORD_AT, dataRec);
		this.createFieldPk(dataRec);
		this.createFieldChildCount(dataRec);

		return(dataRec);
	}

	/**
	 *
	 * This method is analogous to CreateObject method, unlike it this method 
	 * alter the table that should already been created by CreateObject method.
	 *
	 * @param aConn
	 * @param aClass - the abstract class
	 * @param aAvoidRecursive
	 * @param aDepth
	 * @param aInstant - the first instant-able child of the aClass abstract class
	 * @throws Exception
	 */
	private static void AlterObjectForAbstract(Connection aConn, Class<?> aClass, Clasz<?> aInstant, List<ClassAndField> aAvoidRecursive, int aDepth, boolean aDoDdl) throws Exception {
		//Clasz<?> object = aInstant.getClass().newInstance(); // a temporary clszObject to get the fields of aInstan clasz
		Clasz<?> object = aInstant.getClass().getConstructor().newInstance(); // a temporary clszObject to get the fields of aInstan clasz
		Record dataRec = new Record();
		object.getRecordBox().put(Clasz.INSTANT_RECORD_AT, dataRec);
		CreateFieldFromClass(aConn, aClass, object, "", false, aAvoidRecursive, aDepth, aDoDdl); // we create the fields in new clszObject so it can be use for sql alter table...

		if (Database.TableExist(aConn, object.getTableName())) {
			for(Field eachField : object.getInstantRecord().getFieldBox().values()) { // place the abstract fields into the leaf clszObject
				aInstant.createField(aConn, eachField);
				aInstant.cloneField(aConn, eachField);
			}

			if (object.getInstantRecord().totalField() > 0) {
				if (Database.AnyFieldExist(aConn, object.getTableName(), object.getInstantRecord()) == false) {
					App.logInfo("Altering table for class: '" + object.getClass().getSimpleName() + "'");
					Database.AlterTableAddColumn(aConn, object);
					Database.CreateIndexes(aConn, object.getTableName(), object.getInstantRecord());
				}
			}
		} else {
			throw new Hinderance("Cannot alter a non existing table: '" + aInstant.getClaszName() + "', for abstract class: '" + aClass.getSimpleName() + "', check inline flag");
		}
	}


	/**
	 * Function to retrieve the annotation fields declared in a class and convert
	 * them into Fields and place them into the pass in aRoot as the aRoot
	 * properties (in fact its a record at location 0 with those fields)
	 *
	 * @param aMember
	 * @param aParent
	 * @return
	 * @throws Exception
	 */
	private static void CreateFieldFromClass(Connection aConn, Class<?> aMember, Clasz<?> aParent, String fieldPrefix, Boolean aInline, List<ClassAndField> aAvoidRecursive, int aDepth, boolean aDoDdl) throws Exception {
		for(java.lang.reflect.Field reflectField : aMember.getDeclaredFields()) {
			try {
				ReflectField eachAnnotation = (ReflectField) reflectField.getAnnotation(ReflectField.class);
				if (eachAnnotation != null) {
					AttribField attribField = new AttribField();
					FieldType fieldType = eachAnnotation.type();
					attribField.isInline = aInline;
					attribField.fieldName = reflectField.getName();
					attribField.fieldSize = eachAnnotation.size();
					attribField.fieldMask = eachAnnotation.mask();
					attribField.displayPosition = eachAnnotation.displayPosition();
					attribField.polymorphic = eachAnnotation.polymorphic();
					attribField.prefetch = eachAnnotation.prefetch();
					attribField.updateable = eachAnnotation.updateable();
					attribField.changeable = eachAnnotation.changeable();
					attribField.uiMaster = eachAnnotation.uiMaster();
					attribField.lookup = eachAnnotation.lookup();

					ReflectIndex[] reflectIndex = eachAnnotation.indexes();
					if (reflectIndex != null && reflectIndex.length != 0) {
						List<AttribIndex> uniqueIndexes = new CopyOnWriteArrayList<>();
						for (ReflectIndex eachReflect : reflectIndex) {
							AttribIndex attribIndex = new AttribIndex();
							attribIndex.indexName = eachReflect.indexName();
							attribIndex.isUnique = eachReflect.isUnique();
							attribIndex.indexNo = eachReflect.indexNo();
							attribIndex.indexOrder = eachReflect.indexOrder();
							attribIndex.ignoreCase = eachReflect.ignoreCase();
							uniqueIndexes.add(attribIndex);
						}
						attribField.indexes = uniqueIndexes;
					}

					if (Modifier.isStatic(reflectField.getModifiers()) == false) {
						throw new Hinderance("Database field must be of static type: '" + attribField.fieldName + "'");
					}

					// create the field name in database
					String dbFieldName = Clasz.CreateDbFieldName(attribField.fieldName, fieldPrefix);
					if (fieldPrefix == null || fieldPrefix.isEmpty()) {
						reflectField.set(attribField.fieldName, dbFieldName); // set the field name into the static variable
					}

					// programmer definition error, for diagnosing purpose only
					if (fieldType == FieldType.OBJECT || fieldType == FieldType.OBJECTBOX) {
						//if (eachAnnotation.clasz().isEmpty()) {
						if (eachAnnotation.clasz() == null) {
							throw new Hinderance("Error, missing clasz defintion in field: '" + attribField.fieldName + "'");
						}
					}

					if (fieldType == FieldType.OBJECT) {
						boolean doFieldDdl = true;
						boolean isAbstract = FieldObject.IsAbstract(eachAnnotation.clasz());

						if (isAbstract && eachAnnotation.inline()) {
							throw new Hinderance("Abstract field cannot be inline!");
						}

						if (isAbstract) attribField.polymorphic = true; // if abstract field, force it to be polymorphic
						if (eachAnnotation.inline() == true || isAbstract) {
							doFieldDdl = false; // for inline objects or abstract field, no need to create table for it
						}

						boolean fieldCreated = ClassAndFieldExist(aAvoidRecursive, aMember, attribField.fieldName);
						Clasz<?> objField;
						Class<?> objClass;
						//if (PRE_CREATE_OBJECT == false && attribField.prefetch == false) {
						if (attribField.prefetch == false) {
							objField = null;
							objClass = eachAnnotation.clasz();
						} else if (fieldCreated && aDepth > RECURSIVE_DEPTH) {
							objField = new Clasz<>();
							objClass = objField.getClass();
						} else {							aAvoidRecursive.add(new ClassAndField(aMember, attribField.fieldName));

							if (isAbstract) {
								objField = null;
							} else {
								objField = Clasz.CreateObject(aConn, eachAnnotation.clasz(), null, doFieldDdl && aDoDdl, aAvoidRecursive, aDepth + 1, attribField.isInline);
								objField.setMasterObject(aParent);
							}
							if (objField != null) {
								objClass = objField.getClass();
							} else {
								throw new Hinderance("Fail to create object for field: " + attribField.fieldName + ", of class: " + aParent.getClass().getSimpleName());
							}
						}

						if (attribField.isInline == false) {
							aParent.createFieldObject(dbFieldName, (Clasz<?>) objField); // create the field of aRoot type in this aRoot // TODO change this to new FieldObject() for consistency
							aParent.getField(dbFieldName).setModified(false); // placing the clszObject into this new created field do not constitued the field is modified
							aParent.getField(dbFieldName).setInline(eachAnnotation.inline());
							aParent.getField(dbFieldName).deleteAsMember(eachAnnotation.deleteAsMember());
							SetFieldAttrib(aParent.getField(dbFieldName), attribField);
							((FieldClasz) aParent.getField(dbFieldName)).setDeclareType(eachAnnotation.clasz().getName());
							((FieldClasz) aParent.getField(dbFieldName)).setPrefetch(attribField.prefetch);
						}

						if (attribField.isInline == true) {
							if (objClass != Clasz.class) {
								CreateFieldFromClass(aConn, objClass, aParent, dbFieldName, true, aAvoidRecursive, aDepth + 1, aDoDdl); // flatten all inline fields including inline inside an inline onto the master aRoot
							}
						} else {
							if (eachAnnotation.inline() == true) {
								if (objClass != Clasz.class) {
									CreateFieldFromClass(aConn, objClass, aParent, dbFieldName, true, aAvoidRecursive, aDepth + 1, aDoDdl); // flatten all inline fields including inline inside an inline onto the master aRoot
								}
							} else {
								ObjectBase.createMemberOfTable(aConn, aParent.getClass(), objClass, attribField.polymorphic, dbFieldName); // now create the table to associate the "member of" relationship
							}
						}
					} else if (fieldType == FieldType.OBJECTBOX) {
						Class<?> memberClass = eachAnnotation.clasz();
						boolean fieldCreated = ClassAndFieldExist(aAvoidRecursive, aMember, attribField.fieldName);
						Clasz<?> metaObj;
						if (fieldCreated && aDepth > RECURSIVE_DEPTH) {
							metaObj = new Clasz<>();
						} else {
							aAvoidRecursive.add(new ClassAndField(aMember, attribField.fieldName));
							if (FieldObject.IsAbstract(eachAnnotation.clasz())) {
								metaObj = new Clasz<>();
							} else {
								metaObj = Clasz.CreateObject(aConn, memberClass, null, aDoDdl, aAvoidRecursive, aDepth + 1, false);
							}
						}
						metaObj.setMasterObject(aParent);
						aParent.createFieldObjectBox(dbFieldName, new FieldObjectBox<Clasz<?>>(metaObj)); // create the field box inside aRoot
						aParent.getField(dbFieldName).setModified(false);
						aParent.getField(dbFieldName).deleteAsMember(eachAnnotation.deleteAsMember());
						((FieldClasz) aParent.getField(dbFieldName)).setPrefetch(attribField.prefetch);
						SetFieldAttrib(aParent.getField(dbFieldName), attribField);
						((FieldObjectBox<?>) aParent.getField(dbFieldName)).setDeclareType(eachAnnotation.clasz().getName());
						if (aDoDdl) FieldObjectBox.CreateBoxMemberTable(aConn, aParent.getClass(), dbFieldName); // now create the table to associate the array of "member of" relationship
					} else {
						if (fieldType == FieldType.UNKNOWN) {
							throw new Hinderance("Database field type is not define for field: '" + dbFieldName + "'");
						} else if (fieldType == FieldType.STRING && attribField.fieldSize == 0) {
							throw new Hinderance("Database field: '" + dbFieldName + "', of string type cannot have 0 size, in class: '" + aMember.getSimpleName() + "'");
						}

						Field createdField;
						if (attribField.fieldSize <= 0) {
							createdField = aParent.createField(dbFieldName, fieldType);
						} else {
							createdField = aParent.createField(dbFieldName, fieldType, attribField.fieldSize);
						}

						if (attribField.fieldMask.isEmpty() == false) {
							createdField.setFieldMask(attribField.fieldMask);
						}

						SetFieldAttrib(createdField, attribField);
					}
				}
			} catch (Exception ex) {
				throw new Hinderance(ex, "Error at class: '" + aMember.getSimpleName() + "', fail to create field: '" + reflectField.getName() + "'");
			}
		}
	}

	private static void SetFieldAttrib(Field createdField, AttribField aAttribField) throws Exception {
		createdField.setUiMaster(aAttribField.uiMaster);
		createdField.setLookup(aAttribField.lookup);
		createdField.displayPosition(aAttribField.displayPosition);
		createdField.setPolymorphic(aAttribField.polymorphic);
		createdField.setUpdateable(aAttribField.updateable);
		createdField.setChangeable(aAttribField.changeable);

		if (aAttribField.isInline != null && aAttribField.isInline == true) {
			createdField.forDisplay(false); // if is inline field or child of inline field, then is not for display
			createdField.setFlatten(true); // flatten field is not for display
		} else {
			if (createdField.isSystemField()) {
				createdField.forDisplay(false); // system field is never for display 
			} else {
				createdField.forDisplay(true); // default for display attribute
			}
		}

		if (aAttribField.indexes != null && aAttribField.indexes.isEmpty() == false) {
			for (AttribIndex eachIndex : aAttribField.indexes) {
				createdField.indexes.add(eachIndex);
			}
		}
	}

	public static String CreateDbFieldName(String fieldName) {
		return(Clasz.CreateDbFieldName(fieldName, null));
	}

	public static String CreateDbFieldName(String fieldName, String fieldPrefix) {
		String dbFieldName = Database.Java2DbFieldName(fieldName);
		if (fieldPrefix != null && fieldPrefix.isEmpty() == false) {
			dbFieldName = Database.Java2DbFieldName(fieldPrefix) + "_" + dbFieldName; // place prefix for inline fields (not supported yet though)
		}
		return dbFieldName;
	}

	public static Clasz<?> Fetch(Connection aConn, Clasz<?> aCriteria) throws Exception {
		Clasz<?> result;
		result = CreateObject(aConn, aCriteria.getClass()); // use a new clszObject to do slow Fetch, just as fast Fetch, don't touch the aCriteria
		result.copyAllFieldWithModifiedState(aConn, aCriteria);
		result = fetchObjectSlow(aConn, result);
		return result;
	}

	public static <Ty extends Clasz<?>> Ty Fetch(Connection aConn, Class<Ty> aClass, Long aObjId) throws Exception {
		Ty result = ObjectBase.CreateObject(aConn, aClass);
		result.setObjectId(aObjId);
		result = fetchObjectSlow(aConn, result);
		return result;
	}

	public static Clasz<?> FetchFreeType(Connection aConn, Class<?> aClass, Long aObjId) throws Exception {
		Clasz<?> result = ObjectBase.CreateClaszFreeType(aConn, aClass);
		result.setObjectId(aObjId);
		result = fetchObjectSlow(aConn, result);
		return result;
	}

	/**
	 * To be eligible for fast Fetch, there Fetch criteria must be at the leaf
	 * clszObject.
	 *
	 * @param aCriteria
	 * @return
	private static boolean canFastFetch(Clasz<?> aCriteria) {
		boolean isEligible = false;
		for(Field eachField : aCriteria.getInstantRecord().getFieldBox().values()) {
			if (eachField.isModified()) {
				isEligible = true;
				break;
			}
		}

		return(isEligible);
	}
	*/

	/**
	 * This method fetches the record for the leaf clszObject, then thereafter
	 * recursively fetches each of the parent clszObject in the inheritance tree. This
	 * mean if the inheritance tree has X depth, then there will be X sql fetches.
	 * This is slow compare to the fast version that uses ONE sql Fetch
	 * irregardless of the inheritance depth.
	 *
	 * @param aConn
	 * @param aCriteria
	 * @return
	 * @throws Exception
	 */
	private static <Ty extends Clasz<?>> Ty fetchObjectSlow(Connection aConn, Ty aCriteria) throws Exception {
		Ty result = aCriteria;
		Ty objectWithPk;
		if (aCriteria.getObjectId() == null || aCriteria.isPopulated() == false) { // if no objectId, we do fetch for it using given criteria in the object fields
			objectWithPk = FetchUniqueUsingCriteria(aConn, aCriteria);
		} else {
			objectWithPk = aCriteria;
		}

		if (objectWithPk != null) {
			boolean upFetchFound = fetchObjectUpTheTree(aConn, objectWithPk, objectWithPk.getObjectId());
			boolean downFetchFound = fetchObjectDownTheTree(aConn, objectWithPk, aCriteria);
			if (upFetchFound == false && downFetchFound == false) {
				result = null;
			}
		} else {
			result = null;
		}

		return(result);
	}

	/**
	 * This is a tune for Fetch clszObject, the pass in class must be a leaf clszObject as
	 * this method do not do fetching down the tree, only fetching up the tree.
	 *
	 * @param <Ty>
	 * @param aDb
	 * @param aConn
	 * @param aClass
	 * @param aObjectId
	 * @return
	 * @throws Exception
	 */
	public static <Ty extends Clasz<?>> Ty FetchObjectByPk(Connection aConn, Class<Ty> aClass, Long aObjectId) throws Exception {
		Ty result = CreateObject(aConn, aClass);
		fetchObjectUpTheTree(aConn, result, aObjectId);
		return(aClass.cast(result));
	}

	/**
	 * Get the primary key of the aCriteria clszObject by fetching the record
	 * according to the aCriteria fill in fields. The filled values inside the
	 * Clasz clszObject must be one that can uniquely identify a specific clszObject. TODO
	 * validation to ensure field is fill with fields that make up a isUnique key
	 * for that Clasz.
	 *
	 * @param aConn
	 * @param aCriteria
	 * @return
	 * @throws Exception
	 */
	public static <Ty extends Clasz<?>> Ty FetchUniqueUsingCriteria(Connection aConn, Ty aCriteria) throws Exception {
		Ty result = null;

		// set the where record
		Multimap<String, Record> whereBox = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
		ObjectBase.GetLeafSelectCriteria(aConn, aCriteria, whereBox); // the select criteria for this leaf clszObject, doesn't do the parent clszObject
		int cntrRec = 0;

		// place in the where criteria into sql string
		StringBuffer strBuffer = new StringBuffer();
		List<Field> aryWhere = Database.GetWhereClause(aConn, whereBox, strBuffer); // convert the where record into array list
		String sqlStr = "select * from " + Database.GetFromClause(aCriteria.getTableName(), whereBox);
		sqlStr += " where " + strBuffer.toString();

		PreparedStatement stmt = null; // now do the sql Fetch
		ResultSet rs = null;
		try {
			stmt = aConn.prepareStatement(sqlStr);
			Database.SetStmtValue(aConn, stmt, aryWhere);
			rs = stmt.executeQuery();
			while (rs.next()) {
				if (cntrRec > 0) {
					throw new Hinderance("Fetch unique returns more then one record: '" + aCriteria.getClass().getSimpleName() + "', " + stmt.toString());
				}
				aCriteria.setObjectId(rs.getLong(aCriteria.getPkName()));
				result = aCriteria;
				cntrRec++;
			}
		} catch (Exception ex) {
			if (stmt != null) {
				throw new Hinderance(ex, "Fail retrieving pk: " + stmt.toString());
			} else {
				throw new Hinderance(ex, "Fail retrieving pk");
			}
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (stmt != null) {
				stmt.close();
			}
		}

		return(result);
	}

	public static boolean StartDebug = false;

	/**
	 * @param aConn
	 * @param aResult
	 * @param aParentKey
	 * @return 
	 * @throws Exception
	 */
	public static boolean fetchObjectUpTheTree(Connection aConn, Clasz<?> aResult, Long aParentKey) throws Exception {
		boolean result = false;
		String sqlStr;
		Long parentPkValue = null;

		Clasz<?> parentObject = aResult.getParentObjectByContext(); // parent clszObject can be null if parent class is abstract class up the tree until Clasz class
		if (parentObject != null && parentObject.getClass().equals(Clasz.class) == false && ObjectBase.ParentIsNotAbstract(aResult)) {
			sqlStr = "select * from " + aResult.getTableName() + ", " + aResult.getIhTableName(); // got parent class, do the sql matching
			sqlStr += " where " + aResult.getTableName() + "." + aResult.getPkName() + " = " + aResult.getIhTableName() + "." + aResult.getPkName();
			sqlStr += " and " + aResult.getTableName() + "." + aResult.getPkName() + " = ?";
		} else { // if aResult parent is objeck, then it will not have the ih_* table
			sqlStr = "select * from " + aResult.getTableName();
			sqlStr += " where " + aResult.getTableName() + "." + aResult.getPkName() + " = ?";
		}

		FieldLong whereField = new FieldLong(aResult.getPkName());
		whereField.setValueLong(aParentKey);
		List<Field> fieldArr = new CopyOnWriteArrayList<>();
		fieldArr.add(whereField);

		PreparedStatement stmt = null; // now do the sql Fetch
		ResultSet rs = null;
		int cntrRec = 0;
		try {
			stmt = aConn.prepareStatement(sqlStr);
			Database.SetStmtValue(aConn, stmt, fieldArr);
			rs = stmt.executeQuery();
			while (rs.next()) {
				result = true;
				if (cntrRec > 0) {
					throw new Hinderance("Object to fetch criteria returns more then one record: '" + aResult.getClass().getSimpleName() + "'");
				}
				aResult.populateObject(aConn, rs, false);
				if (parentObject != null && parentObject.getClass().equals(Clasz.class) == false) {
					String parentPkName = parentObject.getPkName();
					parentPkValue = rs.getLong(parentPkName);
				}
				cntrRec++;
			}
		} catch (Exception ex) {
			if (stmt != null) 
				throw new Hinderance(ex, "Fail fetching up the inheritance tree: '" + stmt.toString() + "'!");
			else 
				throw new Hinderance(ex, "Fail fetching up the inheritance tree");
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (stmt != null) {
				stmt.close();
			}
		}

		if (cntrRec == 1 && (parentObject != null && parentObject.getClass().equals(Clasz.class) == false)) {
			fetchObjectUpTheTree(aConn, parentObject, parentPkValue);
		}

		return(result);
	}

	//
	// Tune by fetching parent and child in one sql call
	//
	public static void fetchObjectUpTheTree2(Connection aConn, Clasz<?> aResult, Long aParentKey) throws Exception {
		String sqlStr;
		Clasz<?> parentObject = aResult.getParentObjectByContext(); // parent clszObject can be null if parent class is abstract class up the tree until Clasz class
		if (ObjectBase.ParentIsNotAtClaszYet(aResult) && ObjectBase.ParentIsNotAbstract(aResult)) {
			if (parentObject != null && ObjectBase.ParentIsNotAtClaszYet(aResult)) {
				sqlStr = "select * from " + aResult.getTableName() + ", " + aResult.getIhTableName() + ", " + parentObject.getTableName(); // got parent class, do the sql matching
				sqlStr += " where " + aResult.getTableName() + "." + aResult.getPkName() + " = " + aResult.getIhTableName() + "." + aResult.getPkName();
				sqlStr += " and " + aResult.getTableName() + "." + aResult.getPkName() + " = ?";
				sqlStr += " and " + aResult.getTableName() + "." + aResult.getPkName() + " = " + parentObject.getTableName() + "." + parentObject.getPkName();
			} else {
				sqlStr = "select * from " + aResult.getTableName() + ", " + aResult.getIhTableName(); // got parent class, do the sql matching
				sqlStr += " where " + aResult.getTableName() + "." + aResult.getPkName() + " = " + aResult.getIhTableName() + "." + aResult.getPkName();
				sqlStr += " and " + aResult.getTableName() + "." + aResult.getPkName() + " = ?";
			}
		} else { // if aResult parent is objeck, then it will not have the ih_* table
			sqlStr = "select * from " + aResult.getTableName();
			sqlStr += " where " + aResult.getTableName() + "." + aResult.getPkName() + " = ?";
		}
		FieldLong whereField = new FieldLong(aResult.getPkName());
		whereField.setValueLong(aParentKey);
		List<Field> fieldArr = new CopyOnWriteArrayList<>();
		fieldArr.add(whereField);

		PreparedStatement stmt = null; // now do the sql Fetch
		ResultSet rs = null;
		int cntrRec = 0;
		try {
			stmt = aConn.prepareStatement(sqlStr);
			Database.SetStmtValue(aConn, stmt, fieldArr);
			rs = stmt.executeQuery();
			while (rs.next()) {
				if (cntrRec > 0) {
					throw new Hinderance("Object to fetch criteria returns more then one record: '" + aResult.getClass().getSimpleName() + "'");
				}
				aResult.populateObject(aConn, rs, false);
				if (parentObject != null && ObjectBase.ParentIsNotAtClaszYet(aResult)) {
					parentObject.populateObject(aConn, rs, false);
				}
				cntrRec++;
			}
		} catch (Exception ex) {
			if (stmt != null) {
				throw new Hinderance(ex, "Fail fetching up the inheritance tree: " + stmt.toString());
			} else {
				throw new Hinderance(ex, "Fail fetching up the inheritance tree");
			}
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (stmt != null) {
				stmt.close();
			}
		}

		if (parentObject != null) {
			Clasz<?> parentParentObject = parentObject.getParentObjectByContext();
			if (cntrRec == 1 && (parentParentObject != null && ObjectBase.ParentIsNotAtClaszYet(parentObject))) {
				sqlStr = "select * from " + parentObject.getTableName() + ", " + parentObject.getIhTableName(); // got parent class, do the sql matching
				sqlStr += " where " + parentObject.getTableName() + "." + parentObject.getPkName() + " = " + parentObject.getIhTableName() + "." + parentObject.getPkName();
				sqlStr += " and " + parentObject.getTableName() + "." + parentObject.getPkName() + " = ?";
				
				whereField = new FieldLong(parentObject.getPkName());
				whereField.setValueLong(parentObject.getObjectId());
				fieldArr = new CopyOnWriteArrayList<>();
				fieldArr.add(whereField);

				PreparedStatement stmt1 = null;
				ResultSet rs1 = null;
				try {
					stmt1 = aConn.prepareStatement(sqlStr);
					Database.SetStmtValue(aConn, stmt1, fieldArr);
					rs1 = stmt1.executeQuery();
					if (rs1.next()) {
						String parentParentPkName = parentParentObject.getPkName();
						Long parentParentPkValue = rs1.getLong(parentParentPkName);
						fetchObjectUpTheTree(aConn, parentParentObject, parentParentPkValue);
					} else {
						throw new Hinderance("Inheritance database error: " + stmt1.toString());
					}
				} catch (Exception ex) {
					if (stmt1 != null) {
						throw new Hinderance(ex, "Fail fetching up the inheritance tree: " + stmt1.toString());
					} else {
						throw new Hinderance(ex, "Fail fetching up the inheritance tree");
					}
				} finally {
					if (rs1 != null) {
						rs1.close();
					}
					if (stmt1 != null) {
						stmt1.close();
					}
				}
			}
		}
	}

	public static boolean fetchObjectDownTheTree(Connection conn, Clasz<?> aObjectBranch, Clasz<?> aObjectLeaf) throws Exception {
		boolean result = false;
		if (aObjectBranch.getClass().equals(aObjectLeaf.getClass()) == false) { // determine if still to Fetch down the tree
			Clasz<?> childObject = aObjectBranch.getChildObject(conn, aObjectLeaf); // get the immediate child of this branch clszObject, aObjectLeaf will be populated with values from db
			if (childObject != null) {
				result = true;
				if (childObject.getClass().equals(aObjectLeaf.getClass()) == false) {
					fetchObjectDownTheTree(conn, childObject, aObjectLeaf); // recursive Fetch down the tree
				}
			}
		}
		return(result);
	}

	/*
	 * Fetches one clszObject from the database according to the filled in properties
	 * in aCriteria. The criteria can be set in the properties of the clszObject
	 * either at any of its parent, child or instant variable. This method will
	 * parse the clszObject and build up the select criteria to Fetch the clszObject. Can
	 * also chose to Fetch all the fields in the clszObject or just the primary key
	 * for each record in the clszObject, this is use in cases where the structure of
	 * the clszObject is use for deletion. This is known as a fast Fetch because it
	 * uses one sql call to Fetch all the record related to each other in the
	 * inheritance tree.
	 *
	 * TODO: need not Fetch all field if aFetchAllField is set to false (will it improve performance?)
	 *
 */


	/*
	 * Start of the central methods to create field object and field object box
	 */
	public static <Tf extends Clasz<?>> FieldObjectBox<Tf> CreateFieldObjectBoxTransient(Connection aConn, String aName, Class<Tf> aType, Clasz<?> aThis) throws Exception {
		return aThis.createFieldObjectBoxTransient(aConn, aName, aType);
	}

	public <Tf extends Clasz<?>> FieldObjectBox<Tf> createFieldObjectBoxTransient(Connection aConn, String aName, Class<Tf> aType) throws Exception {
		Tf tempMeta = ObjectBase.CreateObjectTransient(aConn, aType);
		FieldObjectBox<Tf> fob = new FieldObjectBox<>(tempMeta);
		return createFieldObjectBox(aName, fob, aType);
	}

	public static <Ty extends Clasz<?>> FieldObjectBox<Ty> CreateFieldObjectBox(Clasz<?> aThis, String aName, FieldObjectBox<Ty> aFieldObjectBox) throws Exception {
		return aThis.createFieldObjectBox(aName, aFieldObjectBox);
	}

	@SuppressWarnings("unchecked")
	public <Tf extends Clasz<?>> FieldObjectBox<Tf> createFieldObjectBox(String aName, FieldObjectBox<Tf> aFieldObjectBox) throws Exception {
		if (aFieldObjectBox == null) {
			return createFieldObjectBox(aName, aFieldObjectBox, (Class<Tf>) UnknownClasz.class); 
		} else {
			return createFieldObjectBox(aName, aFieldObjectBox, (Class<Tf>) aFieldObjectBox.getMetaObj().getClass()); // y need to cast here?
		}
	}

	public static <Tf extends Clasz<?>> FieldObjectBox<Tf> CreateFieldObjectBox(Clasz<?> aThis, String aName, FieldObjectBox<Tf> aFieldObjectBox, Class<Tf> aType) throws Exception {
		return aThis.createFieldObjectBox(aName, aFieldObjectBox, aType);
	}

	public <Tf extends Clasz<?>> FieldObjectBox<Tf> createFieldObjectBox(String aName, FieldObjectBox<Tf> aFieldObjectBox, Class<Tf> aType) throws Exception {
		FieldObjectBox<Tf> result = this.getInstantRecord().createFieldFob(aName, aFieldObjectBox);
		if (aFieldObjectBox != null) {
			if (aFieldObjectBox.getMetaObj() == null) {
				throw new Hinderance("Fail to create FieldObjectBox, its meta object is not define, field name: " + aName);
			}
			if (aFieldObjectBox.getMetaObj().getClass().equals(aType) == false) {
				throw new Hinderance("The pass in clasz object type and its pass in class type is different when trying to create FieldObjectBox: " + aName);
			}
		}
		result.setDeclareType(aType.getName()); // here we allow creating field with null object
		result.setMasterObject(this);
		this.gotCreatedField = true;
		return(result);
	}

	@SuppressWarnings("unchecked")
	public <Tfo extends Clasz<?>> FieldObject<Tfo> createFieldObject(String aName, Tfo aObject) throws Exception {
	//public FieldObject<?> createFieldObject(String aName, Clasz<?> aObject) throws Exception {
		if (aObject == null) {
			return createFieldObject(aName, null, (Class<Tfo>) UnknownClasz.class);
		} else {
			return createFieldObject(aName, aObject, (Class<Tfo>) aObject.getClass());
		}
	}

	public static <Tfo extends Clasz<?>> FieldObject<Tfo> CreateFieldObject(Clasz<?> aThis, String aName, Tfo aObject) throws Exception {
		return aThis.createFieldObject(aName, aObject);
	}

	public static <Tf extends Clasz<?>> FieldObject<Tf> CreateFieldObject(Clasz<?> aThis, String aName, Tf aObject, Class<Tf> aType) throws Exception {
		return aThis.createFieldObject(aName, aObject, aType);
	}

	public <Tf extends Clasz<?>> FieldObject<Tf> createFieldObject(String aName, Tf aObject, Class<Tf> aType) throws Exception {
		FieldObject<Tf> result = this.getInstantRecord().createFieldObject(aName, aObject);
		if (aObject != null && aObject.getClass().equals(aType) == false) {
			throw new Hinderance("The pass in clasz object type and its pass in class type is different when trying to create FieldObject: " + aName);
		}
		result.setDeclareType(aType.getName()); // here we allow creating field with null object
		result.setObj(aObject);
		result.setMasterObject(this);
		//this.claszField.put(aName, result);
		this.gotCreatedField = true;
		return result;
	}
	/* 
	* End of the central methods to create field object and field object box
	*/

	public FieldStr createFieldStr(String aFieldName, String aValue) throws Exception {
		FieldStr result = (FieldStr) this.createField(aFieldName, FieldType.STRING, aValue.length());
		result.setValueStr(aValue);
		return(result);
	}

	public FieldDate createFieldDate(String aFieldName, DateTime aDateValue) throws Exception {
		FieldDate result = (FieldDate) this.createField(aFieldName, FieldType.DATE);
		result.setValueDate(aDateValue);
		return(result);
	}

	public FieldLong createFieldLong(String aFieldName, Long aLong) throws Exception {
		FieldLong result = (FieldLong) this.createField(aFieldName, FieldType.LONG);
		result.setValueLong(aLong);
		return(result);
	}

	public FieldFloat createFieldFloat(String aFieldName, Float aFloat) throws Exception {
		FieldFloat result = (FieldFloat) this.createField(aFieldName, FieldType.FLOAT);
		result.setValueFloat(aFloat);
		return(result);
	}

	public void createField(Connection aConn, Field aField) throws Exception {
		if (aField instanceof FieldObject) {
			Clasz<?> objField = ((FieldObject<?>) aField).getObj();
			this.createFieldObject(aField.getDbFieldName(), (Clasz<?>) objField); // copy by reference for clszObject, no need do deep copy
		} else if (aField instanceof FieldObjectBox) {
			this.createFieldObjectBox(aField.getDbFieldName(), (FieldObjectBox<?>) aField); // copy by reference for clszObject, no need do deep copy
		} else {
			this.getInstantRecord().createField(aConn, aField);
			this.getInstantRecord().getField(aField.getDbFieldName()).setMasterObject(this);
			this.gotCreatedField = true;
		}
	}

	@Override
	public Field createField(String aName, FieldType aType) throws Exception {
		super.createField(aName, aType);
		Field result = this.getInstantRecord().createField(aName, aType);
		result.setMasterObject(this);
		this.gotCreatedField = true;
		return(result);
	}

	@Override
	public Field createField(String aName, FieldType aType, int aSize) throws Exception {
		super.createField(aName, aType, aSize);
		Field result = this.getInstantRecord().createField(aName, aType, aSize);
		result.setMasterObject(this);
		this.gotCreatedField = true;
		return(result);
	}

	public Field createField(String aName, FieldType aType, int aSize, String aValue) throws Exception {
		super.createField(aName, aType, aSize);
		Field result = this.getInstantRecord().createField(aName, aType, aSize, aValue);
		result.setMasterObject(this);
		this.gotCreatedField = true;
		return(result);
	}

	public void cloneField(Connection aConn, Field aField) throws Exception {
		if (aField.getDbFieldType() == FieldType.OBJECT || aField.getDbFieldType() == FieldType.OBJECTBOX) {
			this.getInstantRecord().getField(aField.getDbFieldName()).copyAttribute(aField); // the clszObject field have been shallowed copy, so no need to clone it, just preserve the field attribute
		} else {
			this.getInstantRecord().cloneField(aConn, aField);
		}
	}

	/**
	 * Recursively traverse all the fields, if its modified, return true.
	 *
	 * TODO: tune this, the flag be set in the field clszObject and propagated to the
	 * clszObject when its change
	 *
	 *
	 * @return
	 * @throws Exception
	 *
	 */
	public boolean allOfMyselfIsModified() throws Exception {
		if (this.getClaszName().equals("Clasz")) {
			return(false);
		}

		boolean result = false;
		for(Field eachField : this.getInstantRecord().getFieldBox().values()) {
			try {
				if (eachField.getDbFieldType() == FieldType.OBJECT) {
					if (eachField.isModified()) {
						result = true;
						break;
					} else {
						Clasz<?> obj = ((FieldObject<?>) eachField).getObj();
						if (obj != null) {
							result = obj.allOfMyselfIsModified();
						}
					}
				} else if (eachField.getDbFieldType() == FieldType.OBJECTBOX) {
					if (eachField.isModified()) {
						result = true;
						break;
					} else {
						for(Clasz<?> memberObject : ((FieldObjectBox<?>) eachField).getObjectMap().values()) {
							result = memberObject.allOfMyselfIsModified();
							if (result == true) {
								break;
							}
						}
					}
				} else {
					if (eachField.isModified()) {
						result = true;
					}
				}

				if (result == true) {
					break;
				} else {
					if (ObjectBase.ParentIsNotAtClaszYet(this)) {
						Clasz<?> parentObj = this.getParentObjectByContext();
						if (parentObj != null) {
							result = parentObj.allOfMyselfIsModified();
						}
					}
				}
			} catch (Exception ex) {
				throw new Hinderance(ex, "Error at: '" + this.getClass().getSimpleName() + "', when determining if field: " + eachField.getCamelCaseName() + "' is modified");
			}

		}
		return(result);
	}

	public boolean onlyMyselfIsModified() throws Exception {
		boolean result = false;
		for(Field eachField : this.getInstantRecord().getFieldBox().values()) {
			if (eachField.getDbFieldType() == FieldType.OBJECT) {
				// do nothing, inline field is handled by the usual FieldObject and detected as inLine when persisting 
			} else if (eachField.getDbFieldType() == FieldType.OBJECTBOX) {
				// do nothing, inline field is handled by the usual FieldObjectBox and detected as inLine when persisting 
			} else {
				if (eachField.isModified()) {
					result = true;
				}
			}

			if (result == true) {
				break;
			}
		}
		return(result);
	}

	public FieldClasz getFieldObj(String aFieldName) throws Exception {
		FieldClasz result = (FieldClasz) this.getField(aFieldName);
		return(result);
	}

	public FieldObject<Clasz<?>> getFieldObject(String aFieldName) throws Exception {
		@SuppressWarnings("unchecked")
		FieldObject<Clasz<?>> result = (FieldObject<Clasz<?>>) this.getField(aFieldName);
		return result;
	}

	@SuppressWarnings("unchecked")
	public <Tf extends Clasz<?>> FieldObject<Tf> getFieldObject(String aFieldName, Class<Tf> aClass) throws Exception {
		FieldObject<Tf> result = (FieldObject<Tf>) this.getField(aFieldName);
		return result;
	}

	public FieldObjectBox<?> getFieldObjectBox(String aFieldName) throws Exception {
		FieldObjectBox<?> result = (FieldObjectBox<?>) this.getField(aFieldName);
		return result;
	}

	public <Tf extends Clasz<?>> FieldObjectBox<Tf> getFieldObjectBox(String aFieldName, Class<Tf> aClass) throws Exception {
		FieldObjectBox<Tf> result = GetFieldObjectBox(aClass, this, aFieldName);
		return result;
	}

	@SuppressWarnings("unchecked")
	public static <Tf extends Clasz<?>> FieldObject<Tf> GetFieldObject(Class<Tf> aClass, Clasz<?> aThis, String aFieldName) throws Exception {
		FieldObject<Tf> result = (FieldObject<Tf>) aThis.getField(aFieldName);
		return result;
	}

	@SuppressWarnings("unchecked")
	public static <Tf extends Clasz<?>> FieldObjectBox<Tf> GetFieldObjectBox(Class<Tf> aClass, Clasz<?> aThis, String aFieldName) throws Exception {
		FieldObjectBox<Tf> result = (FieldObjectBox<Tf>) aThis.getFieldObjectBox(aFieldName);
		return result;
	}


	public boolean gotField(String aFieldName) {
		boolean result = true;
		try {
			this.getField(aFieldName);
		} catch(Exception ex) {
			result = false;
		}

		return(result);
	}

	@Override
	public Field getField(String aFieldName) throws Exception {
		try {
			Field result;
			if (aFieldName.equals("objectid")) {
				result = this.getInstantRecord().getFieldLong(this.getPkName());
			} else if (this.fieldExist(aFieldName)) {
				result = this.getInstantRecord().getField(aFieldName);
			} else {
				result = this.getParentObjectByContext().getField(aFieldName);
				if (result == null) {
					throw new Hinderance("Fail to get field: '" + aFieldName + ", no such field in object: '" + this.getClaszName() + "'");
				}
			}
			return(result);
		} catch (Exception ex) {
			throw new Hinderance(ex, "Fail to get the field: '" + aFieldName + "'");
		}
	}

	public FieldInt getFieldInt(String aFieldName) throws Exception {
		FieldInt result = (FieldInt) this.getField(aFieldName);
		return(result);
	}

	@Override
	public boolean fieldExist(String aName) {
		boolean result = false;
		if (this.getInstantRecord() != null) {
			if (this.getInstantRecord().fieldExist(aName)) {
				result = true;
			}
		}
		return(result);
	}

	public List<Field> getLeafField() throws Exception {
		List<Field> result = new CopyOnWriteArrayList<>();
		if (this.getInstantRecord() != null) {
			this.getInstantRecord().getFieldBox().values().forEach((eachField) -> {
				result.add(eachField);
			});
		}
		return(result);
	}

	/**
	 * Get the field definition of this clasz, i.e. the field created with the
	 * CreateField method and all its inherited field
	 *
	 * @return
	 * @throws java.lang.Exception
	 */
	public ConcurrentHashMap<String, Field> refreshTreeField() throws Exception {
		this.loadTreeField();
		return(this.claszField);
	}

	public ConcurrentHashMap<String, Field> getTreeField() throws Exception {
		if (this.claszField.isEmpty() || this.gotFieldDeleted() || this.gotFieldCreated()) {
			this.loadTreeField();
		}
		return(this.claszField);
	}

	public ConcurrentHashMap<String, Field> loadTreeField() throws Exception {
		this.claszField.clear();
		GetTreeField(this, claszField);
		return(this.claszField);
	}

	/**
	 * Recursively go up the inheritance three and get all the field that's been
	 * defined in the meta record.
	 *
	 * @param aClasz
	 * @param aResult
	 * @throws Exception
	 */
	private static void GetTreeField(Clasz<?> aClasz, ConcurrentHashMap<String, Field> aResult) throws Exception {
		try {
			if (aClasz.getInstantRecord() != null) { // recursive clasz will not have any record to it
				for(Field eachField : aClasz.getInstantRecord().getFieldBox().values()) {
					aResult.put(eachField.getDbFieldName(), eachField);
				}

				if (ObjectBase.ParentIsNotAtClaszYet(aClasz)) {
					Clasz<?> parentClasz = aClasz.getParentObjectByContext();
					if (parentClasz != null) { // its possible the parent class for this clszObject is abstract and still not at Clasz
						GetTreeField(parentClasz, aResult);
					}
				}
			}
		} catch (Exception ex) {
			throw new Hinderance(ex, "Fail to flatten the field in class: '" + aClasz.getClass().getSimpleName() + "'");
		}
	}

	public static int compare(Clasz<?> aLeft, Clasz<?> aRight) throws Exception {
		int result = 0;
		//Clasz left = (Clasz) aLeft;
		//Clasz right = (Clasz) aRight;

		// get the sorting key first
		ConcurrentSkipListMap<Integer, Field> sortKey = new ConcurrentSkipListMap<>();
		for(Field eachField : aLeft.getTreeField().values()) {
			if (eachField.isSortKey()) {
				sortKey.put(eachField.getSortKeyNo(), eachField);
			}
		}

		// compare the sort key, with the lowest numbered key first
		for(Field eachField : sortKey.values()) {
			if (eachField.getSortOrder() == null || eachField.getSortOrder() == SortOrder.ASC) {
				result = eachField.compareTo(aRight.getField(eachField.getDbFieldName()));
			} else {
				Field rightField = aRight.getField(eachField.getDbFieldName());
				result = rightField.compareTo(aLeft.getField(rightField.getDbFieldName()));
			}
			if (result != 0) {
				break;
			}
		}

		return(result);
	}

	@Override
	public int compareTo(Object aRight) {
		int result = 0;
		try {
			result = compare(this, (Clasz<?>) aRight);
		} catch (Exception ex) {
			throw new AssertionError("Fail to compare clasz: '" + this.getClass().getSimpleName() + "' and " + aRight.getClass().getSimpleName() + "'" + App.LineFeed + ex.getMessage()); 
		}
		return(result);
	}

	public String getClaszName() {
		return(this.getClass().getSimpleName());
	}

	public void validateBeforePersist(Connection aConn) throws Exception {
	}

	public static Clasz<?> getParentForField(Clasz<?> aClasz, FieldObject<?> aFieldObj) throws Exception {
		Clasz<?> result = null;
		Field field = aClasz.getInstantRecord().getField(aFieldObj.getDbFieldName());
		if (field != null) {
			result = aClasz;
		} else {
			Clasz<?> parentObj = aClasz.getParentObjectByContext();
			if (parentObj == null) {
				throw new Hinderance("There is no field: '" + aFieldObj.getDbFieldName() + "', in: '" + aClasz.getClass().getSimpleName() + "'");
			}
			result = getParentForField(parentObj, aFieldObj);
		}
		return(result);
	}

	/**
	 * Traverse up the inheritance tree and return the parent clszObject that have the
	 * same type of aClass pass in parameter.
	 *
	 * @param aClass
	 * @return
	 * @throws Exception
	 */
	public Clasz<?> getParentObject(Class<?> aClass) throws Exception {
		if (this.getClass().equals(aClass)) {
			return(this);
		}

		Clasz<?> result = null;
		Clasz<?> parentObj = this.getParentObjectByContext();
		try {
			if (parentObj == null) {
				throw new Hinderance("This class: '" + aClass.getSimpleName() + "', do not have parent of the class: '" + this.getClass().getSimpleName() + "'");
			} else if (parentObj.getClass().equals(aClass)) { // if the parent of this class is the same as the class of 'this' clszObject, then this class is the child class
				result = parentObj;
			} else {
				result = parentObj.getParentObject(aClass); // recursive traversing up the tree to get the immediate child class of this clszObject
			}
		} catch (Exception ex) {
			throw new Hinderance(ex, "Fail to get parent object of class: '" + aClass.getSimpleName() + "', from: '" + this.getClass().getSimpleName() + "'");
		}
		return(result);
	}

	/**
	 * Like getMemberObject, this method fetches all the member clszObject from the
	 * database for this array. This method is call when the member field is recursive
	 * in nature. This method is use by the deleteCommit method to retrieve not retrieve 
	 * objects due to recursion.
	 *
	 * @param aConn
	 * @param aFobField
	 * @param aMemberClass
	 * @return
	 * @throws Exception
	 */
	public <Tob extends Clasz<?>> FieldObjectBox<Tob> getMemberObjectBox(Connection aConn, FieldObjectBox<Tob> aFobField, Class<Tob> aMemberClass) throws Exception {
		Clasz<?> masterObj = this.getParentObject(aMemberClass);
		return this.getMemberObjectBox(aConn, aFobField, masterObj, aMemberClass);
	}

	public <Tob extends Clasz<?>> FieldObjectBox<Tob> getMemberObjectBox(Connection aConn, FieldObjectBox<Tob> aFobField, Clasz<?> masterObj, Class<Tob> aMemberClass) throws Exception {
		aFobField.getObjectMap().clear();
		if (aFobField.getMetaObj().getClaszName().equals("Clasz")) {
			Tob metaObj = ObjectBase.CreateObject(aConn, aMemberClass);
			aFobField.setMetaObj(metaObj);
			aFobField.fetchAll(aConn);
		}
		return aFobField;
	}

	public void displayAll(boolean aSet) throws Exception {
		for(Field eachField : this.getTreeField().values()) {
			if (eachField.isSystemField()) {
				eachField.forDisplay(false);
			} else if (eachField.isFlatten()) {
				eachField.forDisplay(false);
			} else {
				eachField.forDisplay(aSet);
			}
		}
	}

	public List<Field> getFieldListByDisplayPosition() throws Exception {
		List<Field> result = new CopyOnWriteArrayList<>(this.getTreeField().values());
		Collections.sort(result, new Comparator<Field>() {
			@Override
			public int compare(Field o1, Field o2) {
				return Integer.valueOf(o1.displayPosition()).compareTo(o2.displayPosition());
			}
    });
		return(result);
	}

	private static String getFqFieldName(String aNowStr, String aNewStr) {
		if (aNowStr.isEmpty() == false) {
			aNowStr += "$";
		}
		return(aNowStr + aNewStr);
	}

	public List<Field> getAllIndexKey() throws Exception {
		List<Field> result = new CopyOnWriteArrayList<>();
		getAllIndexKey(result, this, "");
		return(result);
	}

	private static void getAllIndexKey(List<Field> result, Clasz<?> aClasz, String aFqFieldName) throws Exception {
		for(Field eachField : aClasz.getTreeField().values()) { // no objectid field
			String fqName = getFqFieldName(aFqFieldName, eachField.getDbFieldName());
			if (eachField.isAtomic()) {
				if (eachField.isObjectKey()) {
					eachField.setFqName(fqName);
					result.add(eachField);
				}
			} else {
				if (eachField.getDbFieldType() == FieldType.OBJECT) {
					Clasz<?> clasz = ((FieldObject<?>) eachField).getObj();
					if (clasz != null) {
						getAllIndexKey(result, clasz, fqName); // recursive call to clear the member clszObject index keys
					}
				} else if (eachField.getDbFieldType() == FieldType.OBJECTBOX) {
					Clasz<?> clasz = ((FieldObjectBox<?>) eachField).getMetaObj();
					getAllIndexKey(result, clasz, fqName); // recursive call to clear the clszObject index keys
				} else {
					throw new Hinderance("Invalid field type in object while getting all its index key name: '" + aClasz.getClaszName() + "'");
				}
			}
		}
	}

	public void clearAllIndexKey() throws Exception {
		clearAllIndexKey(this);
	}

	private static void clearAllIndexKey(Clasz<?> aClasz) throws Exception {
		for(Field eachField : aClasz.getTreeField().values()) {
			if (eachField.isAtomic()) {
				if (eachField.isObjectKey()) {
					eachField.setObjectKey(false);
				}
			} else {
				if (eachField.getDbFieldType() == FieldType.OBJECT) {
					Clasz<?> clasz = ((FieldObject<?>) eachField).getObj();
					if (clasz != null) {
						clearAllIndexKey(clasz); 
					}
				} else if (eachField.getDbFieldType() == FieldType.OBJECTBOX) {
					Clasz<?> clasz = ((FieldObjectBox<?>) eachField).getMetaObj();
					clearAllIndexKey(clasz);
				} else {
					throw new Hinderance("Invalid field type in object while getting all its index key name: '" + aClasz.getClaszName() + "'");
				}
			}
		}
	}

	public List<String> getErrorField() {
		return errorField;
	}

	public void setErrorField(CopyOnWriteArrayList<String> errorField) {
		this.errorField = errorField;
	}

	public void clearErrorField() {
		this.getErrorField().clear();
	}

	public void handleError(Exception ex) throws Exception {
		// customize error handling in each class by examaining the exception
	}

	public List<String> getAllErrorField() throws Hinderance {
		CopyOnWriteArrayList<String> result = new CopyOnWriteArrayList<>();
		this.getAllErrorField(result);
		return(result);
	}

	private List<String> getAllErrorField(CopyOnWriteArrayList<String> aAccumField) throws Hinderance {
		if (this.getClaszName().equals("Clasz")) {
			return(aAccumField);
		}

		if (this.getErrorField().size() != 0) {
			aAccumField.addAll(this.getErrorField());
		}

		for(Field eachField : this.getInstantRecord().getFieldBox().values()) {
			try {
				if (eachField.getDbFieldType() == FieldType.OBJECT) {
					Clasz<?> obj = ((FieldObject<?>) eachField).getObj();
					if (obj != null) {
						if (obj.getErrorField().size() != 0) {
							aAccumField.addAll(obj.getErrorField());
						}
						obj.getAllErrorField(aAccumField);
					}
				} else if (eachField.getDbFieldType() == FieldType.OBJECTBOX) {
					for(Clasz<?> memberObject : ((FieldObjectBox<?>) eachField).getObjectMap().values()) {
						memberObject.getAllErrorField(aAccumField);
					}
				} else {
					// atomic fields, do nothing
				}

				if (ObjectBase.ParentIsNotAtClaszYet(this)) {
					Clasz<?> parentObj = this.getParentObjectByContext();
					if (parentObj != null) {
						parentObj.getAllErrorField(aAccumField);
					}
				}
			} catch (Exception ex) {
				throw new Hinderance(ex, "Error at: '" + this.getClass().getSimpleName() + "', when obtaining error field from class: '" + eachField.getDbFieldName() + ";");
			}
		}
		return(aAccumField);
	}

	public interface GetFetchChildSql<Ti> {
		Object execute(Ti aParam) throws Exception;
	}

	public <Ti> void fetchByFilter(Connection aConn, String aChildFieldName, Ti arrayParam, Clasz.GetFetchChildSql<Ti> aChildSql) throws Exception {
		String childSqlStr = (String) aChildSql.execute(arrayParam);
		if (!childSqlStr.isEmpty()) {
			this.getFieldObjectBox(aChildFieldName).fetchByCustomSql(aConn, childSqlStr);
		}
	}

	public Boolean getForDelete() {
		return forDelete;
	}

	public void setForDelete(Boolean forDelete) {
		this.forDelete = forDelete;
	}

	public static <Ty extends Clasz<?>> void ForEachClasz(Connection aConn, Class<Ty> aClass, String aSqlToGetObjId, Callback2ProcessMember<Ty> aCallback) throws Exception {
		List<Field> noSqlParam = new CopyOnWriteArrayList<>();
		ForEachClasz(aConn, aClass, aSqlToGetObjId, noSqlParam, aCallback);
	}

	public static <Ty extends Clasz<?>> void ForEachClaszFreeType(Connection aConn, Class<?> aMasterClass, String aSqlToGetObjId, Callback2ProcessMemberFreeType aCallback) throws Exception {
		List<Field> noSqlParam = new CopyOnWriteArrayList<>();
		ForEachClaszFreeType(aConn, aMasterClass, aSqlToGetObjId, noSqlParam, aCallback);
	}

	public static <Ty extends Clasz<?>> void ForEachClasz(Connection aConn, Class<Ty> aClass, String aSqlToGetObjId, List<Field> aPositionalParamValue, Callback2ProcessMember<Ty> aCallback) throws Exception {
		ResultSetFetch resultSetFetch = new ResultSetFetch();
		resultSetFetch.forEachFetch(aConn, aClass, aSqlToGetObjId, aPositionalParamValue, aCallback);
	}

	public static <Ty extends Clasz<?>> void ForEachClaszFreeType(Connection aConn, Class<?> aClass, String aSqlToGetObjId, List<Field> aPositionalParamValue, Callback2ProcessMemberFreeType aCallback) throws Exception {
		ResultSetFetch resultSetFetch = new ResultSetFetch();
		resultSetFetch.forEachFetchFreeType(aConn, aClass, aSqlToGetObjId, aPositionalParamValue, aCallback);
	}

	// this will clear the fob and fill it with the latest member, so only one member is in the fob after this call, use getFirstMember to get the result
	public void fetchLatestMemberFromFob(Connection aConn, String aBoxName, String aLatestField) throws Exception {
		fetchLatestMemberFromFob(aConn, Clasz.class, aBoxName, aLatestField, null);
	}

	public static <Tf extends Clasz<?>> void FetchLatestMemberFromFob(Connection aConn, Class<Tf> aClass, Clasz<?> aThis, String aBoxName, String aLatestField, Callback2ProcessMember<Tf> aCallback) throws Exception {
		aThis.fetchLatestMemberFromFob(aConn, aClass, aBoxName, aLatestField, aCallback);
	}

	// this will clear the fob and fill it with the latest member, so only one member is in the fob after this call, use getFirstMember to get the result
	@SuppressWarnings("unchecked")
	public <Tf extends Clasz<?>> void fetchLatestMemberFromFob(Connection aConn, Class<Tf> aClass, String aBoxName, String aLatestField, Callback2ProcessMember<Tf> aCallback) throws Exception {
		LambdaGeneric<Clasz<?>> lambdaObject = new LambdaGeneric<>();
		final String fieldLatest = aLatestField;
		FieldObjectBox<Tf> fobOfBoxName = Clasz.GetFieldObjectBox(aClass, this, aBoxName);
		fobOfBoxName.forEachMember(aConn, (Connection bConn, Tf aClasz) -> {
			Clasz<?> latestClasz = lambdaObject.getValue();
			try {
				if (latestClasz == null) {
					if (aCallback != null) {
						if (aCallback.processClasz(bConn, aClasz) == true) {
							lambdaObject.setValue(aClasz);
						}
					} else {
						lambdaObject.setValue(aClasz);
					}
				} else {
					if (latestClasz.getField(fieldLatest) != null) {
						DateTime latestDate;
						DateTime currentDate;
						if (latestClasz.getField(fieldLatest) instanceof FieldDateTime) {
							latestDate = latestClasz.getValueDateTime(fieldLatest);	
							currentDate = aClasz.getValueDateTime(fieldLatest);
						} else {
							latestDate = latestClasz.getValueDate(fieldLatest);	
							currentDate = aClasz.getValueDate(fieldLatest);
						}
						if (latestDate.isBefore(currentDate)) {
							if (aCallback != null) {
								if (aCallback.processClasz(aConn, aClasz) == true) {
									lambdaObject.setValue(aClasz);
								}
							} else {
								lambdaObject.setValue(aClasz);
							}
						}
					}
				}
			} catch(Exception ex) {
				throw new Hinderance(ex, "Fail, exception thrown in Clasz.fetchLatest method.");
			}
			return true;
		});

		Clasz<?> latestClasz = lambdaObject.getValue();
		if (latestClasz != null) {
			this.getFieldObjectBox(aBoxName).removeAll();
			FieldObjectBox<Clasz<?>> fobTemp = (FieldObjectBox<Clasz<?>>) this.getFieldObjectBox(aBoxName);
			fobTemp.addValueObject(latestClasz);
		}
	}

	public void copyValue(Connection aConn, Clasz<?> aSource) throws Exception {
		for(Field eachField : aSource.getInstantRecord().getFieldBox().values()) {
			try {
				if (eachField.getDbFieldType() == FieldType.OBJECT) {
					Clasz<?> memberObj = (Clasz<?>) ((FieldObject<?>) eachField).getObj();
					if (memberObj != null && memberObj.getClaszName().equals("Clasz")) {
						continue;
					}
				}
				if (eachField.getDbFieldType() == FieldType.OBJECTBOX) {
					Clasz<?> memberObj = (Clasz<?>) ((FieldObjectBox<?>) eachField).getMetaObj();
					if (memberObj.getClaszName().equals("Clasz")) {
						continue;
					}
				}

				if (eachField.isPrimaryKey() == false) {
					Field destField = this.getInstantRecord().getField(eachField.getDbFieldName());
					if (eachField.getValueStr().equals(destField.getValueStr()) == false) {
						destField.cloneField(aConn, eachField);
						destField.setModified(true);
					}
				}

			} catch (Exception ex) {
				throw new Hinderance(ex, "Error when copying object : '" + this.getClass().getSimpleName() + "', field: '" + eachField.getDbFieldName() + "'");
			}
		}

		if (ObjectBase.ParentIsNotAtClaszYet(this)) {
			Clasz<?> parntObject = this.getParentObjectByContext();
			Clasz<?> parentSource = aSource.getParentObjectByContext();
			if (parntObject != null) {// its possible the parent class for this clszObject is abstract and still not at Clasz
				parntObject.copyValue(aConn, parentSource);
			}
		}
	}

	public static FieldType GetFieldType(Connection aConn, Class<?> aClass, String aFieldName) throws Exception {
		FieldType fieldType = null;
		Clasz<?> aClasz = ObjectBase.CreateClaszFreeType(aConn, aClass);
		ConcurrentHashMap<String, Field> claszFields = aClasz.getTreeField();
		for(Field eachField : claszFields.values()) {
			if (eachField.getDbFieldName().equals(aFieldName)) {
				fieldType = eachField.getDbFieldType();
				break;
			}
		}

		if (fieldType == null) {
			throw new Hinderance("GetFieldType, no field: " + aFieldName + ", in class: " + aClass.getSimpleName());
		}

		return(fieldType);
	}

	/*
	public CopyOnWriteArrayList<Clasz<?>> getParentObjectByDb(Connection aConn, Class<?> aParent, String aFieldName) throws Exception {
		CopyOnWriteArrayList<Clasz<?>> result = new CopyOnWriteArrayList<>();
		FieldType fieldType = GetFieldType(aConn, aParent, aFieldName);
		if (fieldType == FieldType.OBJECTBOX) { // check if this field at aParent is instant variable clasz field or fieldbox
			result = getAllParentOfMemberFieldBox(aConn, aParent, aFieldName);
		} else if (fieldType == FieldType.OBJECT) {
			result = getParentObjectByDbOfField(aConn, aParent, aFieldName);
		} else {
			throw new Hinderance("getParentObjectByDb, field: " + aParent.getSimpleName() + "." + aFieldName + "is not of OBJECT or OBJECTBOX type!");
		}
		return(result);
	}
	*/

	public CopyOnWriteArrayList<Clasz<?>> getParentObjectByDbOfField(Connection aConn, Class<?> aParent, String aFieldName) throws Exception {
		CannotBeInline(aParent, aFieldName);
		CopyOnWriteArrayList<Clasz<?>> result = new CopyOnWriteArrayList<>();
		String memberTableName = Clasz.GetIvTableName(aParent); // get the iv_ table name
		Table memberTable = new Table(memberTableName); // create the iv_ table
		String parentPkName = CreatePkColName(aParent);
		memberTable.getMetaRec().createField(parentPkName, FieldType.LONG);
		Record whereRecord = new Record();
		whereRecord.createField(aFieldName, this.getObjectId());
		memberTable.fetch(aConn, whereRecord); 
		if (memberTable.totalRecord() == 1) { // can only have one or zero instant variable 
			Long parentOid = memberTable.getRecord(0).getFieldLong(parentPkName).getValueLong();
			Clasz<?> parentClasz = ObjectBase.CreateClaszFreeType(aConn, aParent);
			parentClasz.setObjectId(parentOid);
			result.add(parentClasz);
		}
		return(result);
	}

	// public static CopyOnWriteArrayList<Clasz<?>> GetAllParentOfMemberFieldBox(Connection aConn, Class<?> aParent, Clasz<?> aThis, String aFieldName) throws Exception {
	public static <Ty extends Clasz<?>> CopyOnWriteArrayList<Ty> GetAllParentOfMemberFieldBox(Connection aConn, Class<Ty> aParent, Clasz<?> aThis, String aFieldName) throws Exception {
		return aThis.getAllParentOfMemberFieldBox(aConn, aParent, aFieldName);
	}

	public <Tf extends Clasz<?>> CopyOnWriteArrayList<Tf> getAllParentOfMemberFieldBox(Connection aConn, Class<Tf> aParent, String aFieldName) throws Exception {
		CannotBeInline(aParent, aFieldName);
		CopyOnWriteArrayList<Tf> result = new CopyOnWriteArrayList<>();
		String boxMemberTableName = Clasz.GetIwTableName(aParent, aFieldName); // get the iv_ table name
		Table boxMemberTable = new Table(boxMemberTableName); // create the iv_ table
		String parentPkName = CreatePkColName(aParent);
		boxMemberTable.getMetaRec().createField(parentPkName, FieldType.LONG);
		boxMemberTable.getMetaRec().createField(ObjectBase.LEAF_CLASS, FieldType.STRING, ObjectBase.CLASS_NAME_LEN);
		Record whereRecord = new Record();
		whereRecord.createField(aFieldName, this.getObjectId());
		boxMemberTable.fetch(aConn, whereRecord); // fetches ALL parent entries containing this child clasz
		for(Record eachRec : boxMemberTable.getRecordBox().values()) {
			Long parentOid = eachRec.getFieldLong(parentPkName).getValueLong();
			// Tf eachClasz = ObjectBase.CreateClasz(aConn, aParent);
			Tf eachClasz = ObjectBase.CreateObject(aConn, aParent);
			eachClasz.setObjectId(parentOid);
			result.add(eachClasz);
		}
		return(result);
	}

	public static boolean IsInlineMember(Class<?> aParent, String aMemberName) throws Exception {
		boolean isInline = false;
		java.lang.reflect.Field reflectField = aParent.getField(aMemberName);
		ReflectField eachAnnotation = (ReflectField) reflectField.getAnnotation(ReflectField.class);
		if (eachAnnotation != null) {
			isInline = eachAnnotation.inline();
		} else {
			throw new Hinderance("Error at class: '" + aParent.getSimpleName() + "', fail to create field: '" + reflectField.getName() + "'");
		}
		return(isInline);
	}

	public static void CannotBeInline(Class<?> aParent, String aMemberName) throws Exception {
		if (IsInlineMember(aParent, aMemberName)) {
			throw new Hinderance("The process is not applicable for inline type at, class: " + aParent.getSimpleName() + ", field: " + aMemberName);
		}
	}

	public static Clasz<?> GetInheritanceObject(Clasz<?> aLeafObject, Class<?> aWantedClass) throws Exception {
		Clasz<?> result = null;
		if (ObjectBase.ParentIsNotAtClaszYet(aLeafObject)) {
			Clasz<?> parentObject = aLeafObject.getParentObjectByContext();
			if (parentObject != null ) { 
				if (parentObject.getClass() == aWantedClass) {
					result = parentObject;
				} else {
					result = GetInheritanceObject(parentObject, aWantedClass);
				}
			} else {
				throw new Hinderance("The object: " + aLeafObject.getClass().getName() + " do not inherit class: " + aWantedClass.getName());
			}
		}
		return(result);
	}

	public void removeFieldObjectBoxMember(Connection aConn, String aMemberName, Long aOid, String aClasz) throws Exception {
		boolean removeDone = false;
		this.getFieldObjectBox(aMemberName).fetchByObjectId(aConn, aOid, aClasz);
		while (this.getFieldObjectBox(aMemberName).hasNext(aConn)) {
			Clasz<?> clasz = this.getFieldObjectBox(aMemberName).getNext();
			if (clasz.getObjectId().equals(aOid)) {
				clasz.setForDelete(true);
				removeDone = true;
				break;
			}
		}
		if (removeDone == false) {
			App.logWarn(this, "Attempting to remove member object that do not exist: " + this.getClass().getSimpleName() 
			+ "." + aMemberName + ", oid: " + aOid);
		}
	}

	public void removeFieldObjectBoxMember(Connection aConn, String aMemberName) throws Exception {
		this.getFieldObjectBox(aMemberName).resetIterator(); // always do a reset before starting to loop for the objects
		while (this.getFieldObjectBox(aMemberName).hasNext(aConn)) {
			Clasz<?> clasz = this.getFieldObjectBox(aMemberName).getNext();
			clasz.setForDelete(true);
		}
	}

	public boolean isSame(Clasz<?> aClasz) throws Exception {
		boolean  result = false;
		if (this.getClass().equals(aClasz.getClass())) {
			if (this.getObjectId().equals(aClasz.getObjectId())) {
				result = true;
			}
		}
		return(result);
	}

	// populate this clasz if it has aWantedMember
	// aWantedMember must be a FieldObject and properly populated with oid
	// if aWantedMember is polymorphic, then its polymorphic/leaf clasz must be use
	public boolean populateByMember(Connection aConn, FieldObject<?> aWantedMember) throws Exception {
		boolean result = ObjectBase.PopulateIfGotMember(aConn, this, aWantedMember);
		return result;
	}

	public static <Tf extends Clasz<?>> boolean PopulateByMember(Connection aConn, Clasz<?> aThis, FieldObject<Tf> aWantedMember) throws Exception {
		return aThis.populateByMember(aConn, aWantedMember);
	}

	public static <Tf extends Clasz<?>> boolean PopulateByMember(Connection aConn, Class<Tf> aClass, Clasz<?> aThis, FieldObject<Tf> aWantedMember) throws Exception {
		boolean result = ObjectBase.PopulateIfGotMember(aConn, aThis, aWantedMember);
		return result;
	}

	public boolean equalsCriteria(Connection aConn, Clasz<?> aRealClasz) throws Exception { // cannot just use Ty ?
		boolean result = false;
		if (this.compareForCriteria(aConn, aRealClasz) == 0) {
			result = true;
		}
		return(result);
	}

	public int compareForCriteria(Connection aConn, Clasz<?> aRealClasz) throws Exception {
		int compareInt = -1;
		for(Field eachCriteria : this.getTreeField().values()) {
			if (eachCriteria.isModified()) {
				Field realField = aRealClasz.getField(eachCriteria.getDbFieldName());
				compareInt = eachCriteria.compareForCriteria(aConn, realField);
			}
		}
		return(compareInt);
	}

	public static void SetWhereField(Record recWhere, Field fieldName) throws Exception {
		recWhere.createField(fieldName.getDbFieldName(), fieldName.getDbFieldType(), fieldName.getFieldSize());
		recWhere.getField(fieldName.getDbFieldName()).setValueStr(fieldName.getValueStr());
	}

	public static <Ty extends Clasz<?>> void FetchAll(Connection aConn, Class<Ty> aClass, Callback2ProcessMember<Ty> aCallback) throws Exception {
		String pkColName = Clasz.CreatePkColName(aClass);
		String strSql = "select " + pkColName + " from " + Clasz.CreateTableName(aClass);
		PreparedStatement stmt = null;
		ResultSet rset = null;
		try {
			stmt = aConn.prepareStatement(strSql);
			rset = stmt.executeQuery();
			while (rset.next()) {
				long pk = rset.getLong(1);
				Ty fetchMember = Clasz.Fetch(aConn, aClass, pk);
				if (aCallback != null) {
					if (aCallback.processClasz(aConn, fetchMember) == false) {
						break;
					}
				}
			}
		} catch (Exception ex) {
			if (stmt == null) {
				throw new Hinderance(ex, "FetchAll - Fail to populate object for type: " + aClass.getSimpleName());
			} else {
				throw new Hinderance(ex, "FetchAll - Fail to populate object: " + stmt.toString());
			}
		} finally {
			if (rset != null) {
				rset.close();
			}
			if (stmt != null) {
				stmt.close();
			}
		}
	}

	/* 
	@SuppressWarnings("unchecked")
	public static void FetchParentByBoxMember(Connection aConn, Class<?> aClass, String aMemberName, Clasz<?> aMemberObject, Callback2ProcessMember<?> aCallback) throws Exception {
		if (Clasz.class.isAssignableFrom(aClass) == false) {
			throw new Hinderance("Cannot perform FetchParentByMember for non Clasz class from class: " + aClass.getSimpleName());
		}

		if (aMemberObject.isPopulated() == false) {
			throw new Hinderance("Cannot do FetchParentByMember from non populated member: " + aMemberObject.getClass().getSimpleName());
		}

		String parentPkName = Clasz.CreatePkColName(aClass);
		String memberTableName = GetIwTableName(aClass, aMemberName);
		String memberFieldName = CreateDbFieldName(aMemberName);

		String sqlMember = "select " + parentPkName + " from " + memberTableName + " where " + memberFieldName + " = " + aMemberObject.getObjectId();
		sqlMember += " and leaf_class" + " = '" + aMemberObject.getClass().getName() + "'";

		PreparedStatement stmt = null;
		ResultSet rset = null;
		try {
			stmt = aConn.prepareStatement(sqlMember);
			rset = stmt.executeQuery();
			while (rset.next()) {
				long pk = rset.getLong(1);
				Clasz<?> fetchMember = Clasz.Fetch(aConn, aClass, pk);
				if (aCallback != null) {
					if (aCallback.processClasz(aConn, fetchMember) == false) {
						break;
					}
				}
			}
		} catch (Exception ex) {
			if (stmt == null) {
				throw new Hinderance(ex, "FetchParentByMember - Fail to populate object for type: " + aClass.getSimpleName());
			} else {
				throw new Hinderance(ex, "FetchParentByMember - Fail to populate object: " + stmt.toString());
			}
		} finally {
			if (rset != null) {
				rset.close();
			}
			if (stmt != null) {
				stmt.close();
			}
		}
	}
	*/

	public static <Ty extends Clasz<?>> void FetchParentByMember(Connection aConn, Class<Ty> aParentClass, String aMemberName, Clasz<?> aMemberObject, Callback2ProcessMember<Ty> aCallback) throws Exception {
		if (Clasz.class.isAssignableFrom(aParentClass) == false) {
			throw new Hinderance("Cannot perform FetchParentByMember for non Clasz class from class: " + aParentClass);
		}

		if (aMemberObject.isPopulated() == false) {
			throw new Hinderance("Cannot do FetchParentByMember from non populated member: " + aMemberObject.getClass());
		}

		String parentPkName = Clasz.CreatePkColName(aParentClass);
		String memberTableName = GetIvTableName(aParentClass);
		String memberFieldName = CreateDbFieldName(aMemberName);
		String memberFieldLeaf = CreateLeafClassColName(memberFieldName);

		Table memberTable = new Table(memberTableName);
		memberTable.initMeta(aConn);
		
		String sqlMember = "select " + parentPkName + " from " + memberTableName + " where " + memberFieldName + " = " + aMemberObject.getObjectId();
		if (memberTable.fieldExist(memberFieldLeaf)) {
			sqlMember += " and " + memberFieldLeaf + " = '" + aMemberObject.getClass().getName() + "'";
		}

		PreparedStatement stmt = null;
		ResultSet rset = null;
		try {
			stmt = aConn.prepareStatement(sqlMember);
			rset = stmt.executeQuery();
			while (rset.next()) {
				long pk = rset.getLong(1);
				Ty fetchParent = Clasz.Fetch(aConn, aParentClass, pk);
				if (aCallback != null) {
					if (aCallback.processClasz(aConn, fetchParent) == false) {
						break;
					}
				}
			}
		} catch (Exception ex) {
			if (stmt == null) {
				throw new Hinderance(ex, "FetchParentByMember - Fail to populate object for type: " + aParentClass.getSimpleName());
			} else {
				throw new Hinderance(ex, "FetchParentByMember - Fail to populate object: " + stmt.toString());
			}
		} finally {
			if (rset != null) {
				rset.close();
			}
			if (stmt != null) {
				stmt.close();
			}
		}
	}

	public Float getValueFloat(String aFieldName) throws Exception {
		Float result;
		if (this.fieldExist(aFieldName)) {
			result = this.getInstantRecord().getValueFloat(aFieldName);
		} else {
			this.validateField(aFieldName);
			result = (this.getParentObjectByContext().getValueFloat(aFieldName));
		}
		return(result);
	}

	public void setValueFloat(String aFieldName, Float aFieldValue) throws Exception {
		if (this.fieldExist(aFieldName)) {
			this.getInstantRecord().setValueFloat(aFieldName, aFieldValue);
		} else {
			this.validateField(aFieldName);
			this.getParentObjectByContext().setValueFloat(aFieldName, aFieldValue);
		}
	}

	//
	// renaming underlying dabase object due to renaming of Clasz name
	//
	public static String CreateTableName(String aClassSimpleName) {
		String result = Clasz.GetTableNamePrefix() + Database.Java2DbTableName(aClassSimpleName);
		return(result);
	}

	public static String GetIvTableName(String aChildSimpleName) {
		String ivName = Clasz.GetIvPrefix() + Database.Java2DbTableName(aChildSimpleName); // create the parent iv_ table
		return(ivName);
	}

	public static String CreateSequenceTableName(String aSimpleName) {
		String result = Clasz.GetSequenceNamePrefix() + Database.Java2DbTableName(aSimpleName);
		return(result);
	}

	public static String CreatePkColName(String aClassSimpleName) throws Exception {
		String result = CreateTableName(aClassSimpleName) + "_pk";
		return(result);
	}

	public static String CreateChildCountColName(String aClassSimpleName) throws Exception {
		String result = CreateTableName(aClassSimpleName) + Table.POSTFIX_FIELD_CHILD_COUNT;
		return(result);
	}

	public static String GetIwTableName(String aParent, String aFieldName) {
		String iwName = Clasz.GetIwPrefix() + Database.Java2DbTableName(aParent) + "_" + Database.Java2DbTableName(aFieldName); // create the parent iv_ table
		return(iwName);
	}

	// not use, use the string version below instead
	public static void RenameClaszDbObject(Connection aConn, Class<?> aOldClass, String aNewClassSimpleName) throws Exception {
		String classFqn = aOldClass.getName();
		String packageName = classFqn.substring(0, classFqn.lastIndexOf("."));
		String oldClassSimpleName = classFqn.substring(classFqn.lastIndexOf(".") + 1);
		RenameClaszDbObject(aConn, packageName, oldClassSimpleName, aNewClassSimpleName);
	}

	public static void RenameClaszDbObject(Connection aConn, String aPackageName, String aOldClassSimpleName, String aNewClassSimpleName) throws Exception {
		String oldClassFqn = aPackageName + "." + aOldClassSimpleName;

		// ------------------------------------------------------------------------------------------
		// rename cz_
		// ------------------------------------------------------------------------------------------

		// create both new and old table name
		String tableNameOld = CreateTableName(aOldClassSimpleName);
		String tableNameNew = CreateTableName(aNewClassSimpleName);

		// rename cc, pk column in main table
		String pkColNameOld = CreatePkColName(aOldClassSimpleName);
		String pkColNameNew = CreatePkColName(aNewClassSimpleName);
		String ccColNameOld = CreateChildCountColName(aOldClassSimpleName);
		String ccColNameNew = CreateChildCountColName(aNewClassSimpleName);
		Table oldTable = new Table(tableNameOld);

		// rename main table name to the new name
		try {
			oldTable.renameTable(aConn, tableNameNew);
			App.logInfo("Completed renaming table of: " + aOldClassSimpleName);
		} catch (Exception ex) {
			App.logEror(ex, "Error when renaming table of: " + aOldClassSimpleName);
		}

		// rename new main table pk constraint/index name 
		Table newTable = new Table(tableNameNew);
		try {
			newTable.renamePrimaryKey(aConn, tableNameOld);
			App.logInfo("Completed renaming pk constraint of: " + aOldClassSimpleName);
		} catch (Exception ex) {
			App.logEror(ex, "Error when renaming pk constraint of: " + aOldClassSimpleName);
		}

		try {
			newTable.renameField(aConn, pkColNameOld, pkColNameNew);
			App.logInfo("Completed renaming pk column of: " + aOldClassSimpleName);
		} catch(Exception ex) {
			App.logEror(ex, "Error when renaming pk column of: " + aOldClassSimpleName);
		}

		try {
			newTable.renameField(aConn, ccColNameOld, ccColNameNew);
			App.logInfo("Completed renaming cc column of: " + aOldClassSimpleName);
		} catch(Exception ex) {
			App.logEror(ex, "Error when renaming cc column of: " + aOldClassSimpleName);
		}


		boolean gotFieldObject = false;
		try {
			// for each fieldobjectbox, rename it's table name and pk col name
			boolean gotError = false;
			Class<?> oldClass = Class.forName(oldClassFqn);
			Clasz<?> claszOld = ObjectBase.CreateObjectTransientFromAnyClass(aConn, oldClass); // use transient to avoid re-creating the table when run multiple time
			List<Field> leafFieldList = claszOld.getLeafField();
			for(Field eachField : leafFieldList) {
				if (eachField instanceof FieldObjectBox) {
					App.logDebg("Processing fob field name: " + eachField.getDbFieldName());
					String iwBoxNameOld = GetIwTableName(oldClass, eachField.getDbFieldName()); // get the iv_ table name
					String iwBoxNameNew = GetIwTableName(aNewClassSimpleName, eachField.getDbFieldName());
					Table oldIvBoxTable = new Table(iwBoxNameOld);

					try { ((FieldObjectBox<?>) eachField).renameBoxMemberUniqueIndex(aConn, oldClass, aNewClassSimpleName); } catch(Exception ex) {
						gotError = true;
						App.logEror(ex, "Error when renaming fob unique index");
					}
					try { oldIvBoxTable.renameTable(aConn, iwBoxNameNew); } catch(Exception ex) {
						gotError = true;
						App.logEror(ex, "Error when renaming fob table to its new name: " + iwBoxNameNew);
					}
					Table newIvBoxTable = new Table(iwBoxNameNew);
					try { newIvBoxTable.renameField(aConn, pkColNameOld, pkColNameNew); } catch(Exception ex) {
						gotError = true;
						App.logEror(ex, "Error when renaming fob pk field name: " + pkColNameOld + ", " + pkColNameNew);
					}
				} else 	if (eachField instanceof FieldObject<?>) {
					gotFieldObject = true;
				}
			}
			if (gotError == false) App.logInfo("Completed renaming FieldObjectBox of: " + aOldClassSimpleName);
		} catch (Exception ex) {
			App.logEror(ex, "Error when renaming FieldObjectBox of: " + aOldClassSimpleName);
		}

		// ------------------------------------------------------------------------------------------
		// rename ih_ -- yet to implement this, need to do it...
		// ------------------------------------------------------------------------------------------

		// ------------------------------------------------------------------------------------------
		// rename ih_ -- yet to implement this, need to do it...
		// ------------------------------------------------------------------------------------------

		// ------------------------------------------------------------------------------------------
		// rename ih_ -- yet to implement this, need to do it...
		// ------------------------------------------------------------------------------------------

		// ------------------------------------------------------------------------------------------
		// rename ih_ -- yet to implement this, need to do it...
		// ------------------------------------------------------------------------------------------


		// ------------------------------------------------------------------------------------------
		// rename iv_
		// ------------------------------------------------------------------------------------------

		// rename pk column in iv table
		if (gotFieldObject == true) {
			// rename iv table
			String ivNameOld = GetIvTableName(aOldClassSimpleName);
			String ivNameNew = GetIvTableName(aNewClassSimpleName);
			try {
				Table oldIvTable = new Table(ivNameOld);
				oldIvTable.renameTable(aConn, ivNameNew);
				App.logInfo("Completed renaming iv_ table of: " + aOldClassSimpleName);
			} catch (Exception ex) {
				App.logEror(ex, "Error when renaming iv_ table of: " + aOldClassSimpleName);
			}

			Table newIvTable = new Table(ivNameNew);
			try {
				newIvTable.renameField(aConn, pkColNameOld, pkColNameNew);
				App.logInfo("Completed renaming pk of iv_ table of: " + aOldClassSimpleName);
			} catch (Exception ex) {
				App.logEror(ex, "Eror when renaming pk of iv_ table of: " + aOldClassSimpleName);
			}
		}


		// ------------------------------------------------------------------------------------------
		// rename sq_
		// ------------------------------------------------------------------------------------------

		// rename sequence table
		try {
			String sqNameOld = CreateSequenceTableName(aOldClassSimpleName);
			String sqNameNew = CreateSequenceTableName(aNewClassSimpleName);
			Table oldSqTable = new Table(sqNameOld);
			oldSqTable.renameTable(aConn, sqNameNew);
			App.logInfo("Completed renaming sq_ table of: " + aOldClassSimpleName);
		} catch (Exception ex) {
			App.logEror(ex, "Eror when renaming sq_ table of: " + aOldClassSimpleName);
		}
	}

	public static void RenameLeafClass(Connection aConn, String aPackageName, String aOldClassSimpleName, String aNewClassSimpleName, String aFobParent, String aFobFieldName) throws Exception {
		String oldIwBoxTableName = GetIwTableName(aFobParent, aFobFieldName);
		Clasz.RenameLeafClassByIvBoxName(aConn, oldIwBoxTableName, aPackageName, aOldClassSimpleName, aNewClassSimpleName);
	}

	public static void RenameLeafClassByIvBoxName(Connection aConn, String newIvBoxTableName, String aPackageName, String aOldClassSimpleName, String aNewClassSimpleName) throws Exception {
		Table newIvBoxTable = new Table(newIvBoxTableName);
		newIvBoxTable.initMeta(aConn);
		newIvBoxTable.forEachRecord(aConn, (Connection bConn, Record aRecord) -> {
			String leafClass = aRecord.getField(ObjectBase.LEAF_CLASS).getValueStr();
			String oldClassName = aPackageName + "." + aOldClassSimpleName;
			String oldClassSimpleName = aOldClassSimpleName;
			if (leafClass.equalsIgnoreCase(oldClassName)) {
				String newClassName = oldClassName.replace(oldClassSimpleName, aNewClassSimpleName);
				Record record2Update = new Record();
				record2Update.createField(ObjectBase.LEAF_CLASS, newClassName);
				newIvBoxTable.update(bConn, record2Update, aRecord);
				if (bConn.getAutoCommit() == false) bConn.commit();
			}
			return(true);
		});
	}

	public Long getAncestorOid(Class<?> aClass) throws Exception {
		if (this.getClass() == aClass) {
			return this.getInstantRecord().getFieldLong(this.getPkName()).getValueLong();
		} else {
			Clasz<?> parentObj = this.getParentObjectByContext();
			if (parentObj.getClass().equals(Clasz.class) == false) {
				return parentObj.getAncestorOid(aClass);
			} else {
				throw new Hinderance("Fail to get ancestor oid, already at highest parent class: " + this.getClass().getSimpleName());
			}
		}
	}

	private static Multimap<String, Record> LinkParentWithFob(FieldObjectBox<?> aFobField) throws Exception {
		Multimap<String, Record> whereBox = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
		String iwBoxTableName = Clasz.GetIwTableName(aFobField);

		Record linkIwBox2Member = new Record();
		linkIwBox2Member.createField(aFobField.getDbFieldName(), "");
		String memberTableName = Clasz.CreateTableName(aFobField.getMemberClass());
		String memberPkName = Clasz.CreatePkColName(aFobField.getMemberClass());
		linkIwBox2Member.getField(aFobField.getDbFieldName()).setFormulaStr(iwBoxTableName + "." + aFobField.getDbFieldName() + " = " + memberTableName + "." + memberPkName);
		whereBox.put(iwBoxTableName, linkIwBox2Member);

		Record linkParent2Member = new Record();
		String parentPkName = Clasz.CreatePkColName(aFobField.getMasterClass());
		String parentTableName = Clasz.CreateTableName(aFobField.getMasterClass());
		linkParent2Member.createField(parentPkName, "");
		linkParent2Member.getField(parentPkName).setFormulaStr(iwBoxTableName + "." + parentPkName + " = " + parentTableName + "." + parentPkName);
		whereBox.put(iwBoxTableName, linkParent2Member);

		return whereBox;
	}

	private static List<Object> Link2DeepParentClause(Connection aConn, List<Clasz<?>> aClasz2Link, List<String> aFobFieldName) throws Exception {
		String fromStr = "";

		List<Clasz<?>> clasz2Link = new CopyOnWriteArrayList<>();
		// get the real clasz with its fob field, not their subclass clasz (to get tablename for fromStr)
		if (aClasz2Link.size() == aFobFieldName.size()) {
			for(int cntr = 0; cntr < aClasz2Link.size(); cntr++) {
				Clasz<?> eachClasz = aClasz2Link.get(cntr);
				String eachFieldName = aFobFieldName.get(cntr);
				eachClasz = GetRealClaszOfField(eachClasz, eachFieldName);
				clasz2Link.add(eachClasz);
				String tableName = Clasz.CreateTableName(eachClasz);
				if (fromStr.isEmpty() == false && fromStr.trim().endsWith(",") == false) fromStr += ", ";
				fromStr += tableName;
			}
		} else {
			throw new Hinderance("Fail to deep link child to parent, clasz and field array size not the same");
		}

		// get the wherebox of all the linked parent
		List<Multimap<String, Record>> whereBoxList = Link2DeepParentWhereBox(clasz2Link, aFobFieldName); 

		// get there criteria for each of the deep clasz
		for(int cntr = 0; cntr < clasz2Link.size(); cntr++) {
			Clasz<?> eachClasz = clasz2Link.get(cntr);
			if (eachClasz.allOfMyselfIsModified()) {
				Multimap<String, Record> whereBox = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
				ObjectBase.GetLeafSelectCriteria(aConn, eachClasz, whereBox); // the select criteria for this leaf clszObject, doesn't do the parent clszObject
				whereBoxList.add(whereBox);
			}
		}
		
		return Multimap2SqlClause(aConn, whereBoxList, fromStr);
	}

	private static List<Object> Multimap2SqlClause(Connection aConn, List<Multimap<String, Record>> whereBoxList, String fromStr) throws Exception {
		StringBuilder whereStr = new StringBuilder();
		List<Field> fieldArrCombine = new CopyOnWriteArrayList<>(); 

		// convert all the where into from and where clause with its bind array
		StringBuilder fromStrBuff = new StringBuilder(fromStr);
		for(Multimap<String, Record> eachWhereBox : whereBoxList) {
			eachWhereBox.asMap().forEach((eachTableName, collection) -> {
				collection.forEach(eachWhereRec -> {
					try {
						StringBuffer whereStrTmp = new StringBuffer();
						List<Field> fieldArr = Database.GetWhereClause(aConn, eachTableName, eachWhereRec, whereStrTmp);
						if (whereStrTmp.toString().isEmpty() == false) {
							if (whereStr.toString().isEmpty() == false) whereStr.append(" and ");
							whereStr.append(whereStrTmp);
							fieldArrCombine.addAll(fieldArr);
						}
					} catch(Exception ex) {
						//App.logEror(Database.class, ex, "Fail to get where clause from multimap");
						throw new RuntimeException(ex);
					} 
				});
				String tableNameCsv = Database.GetFromClause(eachTableName, eachWhereBox).trim();
				if (tableNameCsv.isEmpty() == false) {
					String[] tableNameList = tableNameCsv.split(",");
					for(String eachName: tableNameList) {
						eachName = eachName.trim();
						String fromString = fromStrBuff.toString();
						//if (fromString.contains(eachName) == false) {
						if (Generic.CsvContainStr(fromString, eachName) == false) {
							if (fromString.isEmpty() == false && fromString.trim().endsWith(",") == false) fromStrBuff.append(", ");
							fromStrBuff.append(eachName);
						}
					}
				}
			});
		}
		fromStr = fromStrBuff.toString();

		// result is fieldArr for binding, fromClause, whereClause
		List<Object> result = new CopyOnWriteArrayList<>();
		result.add(fieldArrCombine);
		result.add(fromStr);
		result.add(whereStr.toString());
		return result;
	}

	private static List<Multimap<String, Record>> Link2DeepParentWhereBox(List<Clasz<?>> aClasz2Link, List<String> aFobFieldName) throws Exception {
		List<Multimap<String, Record>> whereBoxList = new CopyOnWriteArrayList<>();
		if (aClasz2Link.size() == aFobFieldName.size()) {
			for(int cntr = 0; cntr < aClasz2Link.size(); cntr++) {
				Clasz<?> eachClasz = aClasz2Link.get(cntr);
				String eachFieldName = aFobFieldName.get(cntr);
				FieldObjectBox<?> eachFob = eachClasz.getFieldObjectBox(eachFieldName);
				Multimap<String, Record> whereBox = LinkParentWithFob(eachFob);
				whereBoxList.add(whereBox);
			}
		} else {
			throw new Hinderance("Fail to deep link child to parent, clasz and field array size not the same");
		}
		return whereBoxList;
	}

	public static Clasz<?> GetRealClaszOfField (Clasz<?> aVirtualClasz, String aFieldName) throws Exception {
		Clasz<?> result;
		if (ObjectBase.IsNotAtClaszYet(aVirtualClasz)) {
			if (aVirtualClasz.fieldExist(aFieldName)) {
				result = aVirtualClasz;
			} else {
				result = GetRealClaszOfField(aVirtualClasz.getParentObjectByContext(), aFieldName);
			}
		} else {
			result = null;
		}
		return result;
	}

	private static String InsertFrom2Sql(String aFullSql, String aCsvTableName) throws Exception {
		String result;
		String csvNoDuplicate;

		String fullSql = aFullSql.trim().replaceAll(";", "");
		String csvTableName = aCsvTableName.trim().toLowerCase();
		String startPart = "";
		String fromPart = "";
		String wherePart = "";
		int posStartFrom = fullSql.indexOf("from");
		int posStartWhere = fullSql.indexOf("where");
		if (posStartFrom < 0) {
			throw new Hinderance("Attempting to insert table names into invalid sql string: " + aFullSql);
		} else {
			startPart = fullSql.substring(0, posStartFrom);
			if (posStartWhere < 0) {
				fromPart = fullSql.substring(posStartFrom);
			} else {
				fromPart = fullSql.substring(posStartFrom, posStartWhere);
				wherePart = fullSql.substring(posStartWhere).trim();
			}
		}

		if (posStartFrom < 0) {
			throw new Hinderance("Fail to insert from clause, expecting a valid sql string");
		} else {
			csvNoDuplicate = fromPart.toLowerCase().replaceAll("from ", "").trim();
			String onlyTable = csvTableName.replaceAll("from ", "");
			String[] tableNames = onlyTable.split(",");
			for(String eachTableName : tableNames) {
				eachTableName = eachTableName.trim();
				if (eachTableName.isEmpty() == false) {
					if (Generic.CsvContainStr(csvNoDuplicate, eachTableName) == false) {
						if (csvNoDuplicate.isEmpty() == false) csvNoDuplicate += ", ";
						csvNoDuplicate += eachTableName;
					}
				}
			}
		}
		csvNoDuplicate = csvNoDuplicate.trim();

		result = startPart.trim();
		//if (fromPart.isEmpty() == false) result += " " + fromPart;
		if (csvNoDuplicate.isEmpty() == false) result += " from " + csvNoDuplicate;
		if (wherePart.isEmpty() == false) result += " " + wherePart;
		return result.trim();
	}

	/*
	 * This differs from the one in FieldObjectBox.GetEachFieldExpression, 
	 * as this is fetching directly from the table without objectindex, this one also is with inheritance
	 * this should be the universal version and replace GetEachFieldExpression()
	 *
	*/


	private static <Ty extends Clasz<?>> List<Object> SqlForPaging(Connection aConn, List<Ty> aKeyClasz, List<String> aKeyField, List<String> aKeyValue, List<SortOrder> aKeyOrder, String aPageDirection) throws Exception {
		if (aKeyClasz.size() != aKeyField.size() && aKeyField.size() != aKeyValue.size()) {
			throw new Hinderance("When SqlForPaging, its total key clasz, key field and key value must be the same!");
		}

		// accumulate each sort set and sent it for cancatenation
		List<Clasz<?>> sameOrderKeyClasz = new CopyOnWriteArrayList<>();
		List<String> sameOrderKeyField = new CopyOnWriteArrayList<>();
		List<String> sameOrderKeyValue = new CopyOnWriteArrayList<>();
		List<SortOrder> sameOrderKeyOrder = new CopyOnWriteArrayList<>();

		String strFrom = "";
		String resultWhereExpression = "";
		String resultSortOrder = "";
		String firstSetWhereExpression = "";
		int sortOrderBatchCntr = 0;
		boolean allValueIsEmpty = true;
		for(int cntrSort = 0; cntrSort < aKeyOrder.size(); cntrSort++) {
			SortOrder currentSortOrder = aKeyOrder.get(cntrSort);
			SortOrder nextSortOrder = null;
			if (cntrSort + 1 < aKeyOrder.size()) {
				nextSortOrder = aKeyOrder.get(cntrSort + 1);
			}

			sameOrderKeyClasz.add(aKeyClasz.get(cntrSort));
			sameOrderKeyField.add(aKeyField.get(cntrSort));
			sameOrderKeyValue.add(aKeyValue.get(cntrSort));
			sameOrderKeyOrder.add(aKeyOrder.get(cntrSort));

			// handle from clause
			String keyName = aKeyField.get(cntrSort).toLowerCase();
			Clasz<?> leafClasz = aKeyClasz.get(cntrSort);
			Clasz<?> claszOfField = GetRealClaszOfField(leafClasz, keyName);
			String tableNameOfField = Clasz.CreateTableName(claszOfField).toLowerCase();
			if (strFrom.isEmpty() == false) strFrom += ", ";
			if (Generic.CsvContainStr(strFrom, tableNameOfField) == false) strFrom += tableNameOfField;

			// different sort order, start getting the sql
			if (nextSortOrder == null || currentSortOrder.equals(nextSortOrder) == false) { 
				List<Object> sameSortOrder = GetSqlForPaging(aConn, sameOrderKeyClasz, sameOrderKeyField, sameOrderKeyValue, sameOrderKeyOrder, aPageDirection);
				String strWhereExpression = ((String) sameSortOrder.get(0)).trim();
				String strSortOrder = ((String) sameSortOrder.get(1)).trim();
				boolean isEmptyValue = (boolean) sameSortOrder.get(2);
				if (isEmptyValue == false) allValueIsEmpty = false; 

				// no big, less or equal for last section set where str to ensure fetch is next haven't display record
				if (cntrSort == aKeyOrder.size() - 1 && aPageDirection.equals("seek") == false) {
					strWhereExpression = strWhereExpression.replaceAll(">=", ">");
					strWhereExpression = strWhereExpression.replaceAll("<=", "<");
				}

				// after doing set section, need to or into set with primary key set and no other keys set
				if (sortOrderBatchCntr == 0) {
					firstSetWhereExpression = strWhereExpression;
					if (aPageDirection.equals("seek") == false) {
						firstSetWhereExpression = firstSetWhereExpression.replaceAll(">=", ">");
						firstSetWhereExpression = firstSetWhereExpression.replaceAll("<=", "<");
					}
				} 

				// place there where and sort clause into result
				if (strWhereExpression.isEmpty() == false) {
					if (resultWhereExpression.isEmpty() == false) resultWhereExpression += " and ";
					resultWhereExpression += strWhereExpression;
				}
				if (strSortOrder.isEmpty() == false) {
					if (resultSortOrder.isEmpty() == false) resultSortOrder += ", ";
					resultSortOrder += strSortOrder;
				}

				// start with new sort batch
				sortOrderBatchCntr++;
				sameOrderKeyField.clear();
				sameOrderKeyValue.clear();
				sameOrderKeyOrder.clear();
			}
		}

		if (allValueIsEmpty == true) {
			resultWhereExpression = "";
		} else {
			resultWhereExpression = "(" + resultWhereExpression + ")" + " or " + "(" + firstSetWhereExpression + ")";
		}

		List<Object> result = new CopyOnWriteArrayList<>();
		result.add(strFrom);
		result.add(resultWhereExpression);
		result.add(resultSortOrder);
		return result;
	}

	private static List<Object> GetSqlForPaging(Connection aConn, List<Clasz<?>> aKeyClasz, List<String> aKeyField, List<String> aKeyValue, List<SortOrder> aSortOrder, String aPageDirection) throws Exception {
		String concatFieldName = "";
		String concatFieldValue = "";
		String strOrder = "";
		for(int cntrField = 0; cntrField < aKeyField.size(); cntrField++) {
			String keyName = aKeyField.get(cntrField).toLowerCase();
			String keyValue = aKeyValue.get(cntrField);
			SortOrder sortOrder = aSortOrder.get(cntrField);
			if (keyValue == null) keyValue = "";

			Clasz<?> leafClasz = aKeyClasz.get(cntrField);
			Clasz<?> claszOfField = GetRealClaszOfField(leafClasz, keyName);
			String tableNameOfField = Clasz.CreateTableName(claszOfField).toLowerCase();
			Table tableOfField = new Table(tableNameOfField);
			tableOfField.initMeta(aConn);

			//if (concatFieldName.isEmpty() == false) concatFieldName += " || ' ' || ";
			if (concatFieldName.isEmpty() == false) {
				if (Database.GetDbType(aConn) == DbType.MYSQL) {
					concatFieldName += ", ";
				} else {
					concatFieldName += " || ";
				}
			}

			// pad column and value according to field type
			Field keyField = tableOfField.getField(keyName);
			if (keyField.getDbFieldType() == FieldType.DATETIME) {
				String dateForSort = DateAndTime.FormatDateTimeForSort(keyValue);
				concatFieldValue += dateForSort;
				concatFieldName += Database.DateTimeForSort(aConn, tableNameOfField + "." + keyName);
			} else if (keyField.getDbFieldType() == FieldType.DATE) {
				String dateForSort = DateAndTime.FormatDateForSort(keyValue);
				concatFieldValue += dateForSort;
				concatFieldName += Database.DateForSort(aConn, tableNameOfField + "." + keyName);
			} else if (keyField.getDbFieldType() == FieldType.INTEGER || keyField.getDbFieldType() == FieldType.LONG) {
				String digitForSort = Generic.PadDigitForSort(keyValue);
				concatFieldValue += digitForSort;
				String sqlDigit2Str = Database.Num2StrSql(aConn, tableNameOfField + "." + keyName);
				concatFieldName += Database.LeftPadSql(aConn, sqlDigit2Str, digitForSort.length(), " ");
			} else if (keyField.getDbFieldType() == FieldType.FLOAT) {
				String digitForSort = Generic.PadFloatForSort(keyValue);
				concatFieldValue += digitForSort;
				String sqlDigit2Str = Database.Float2StrSql(aConn, tableNameOfField + "." + keyName);
				concatFieldName += Database.LeftPadSql(aConn, sqlDigit2Str, digitForSort.length(), " ");
			} else {
				String strForSort = Generic.PadStrForSort(keyValue.toLowerCase(), keyField.getFieldSize());
				concatFieldValue += strForSort;
				concatFieldName += Database.RightPadSql(aConn, tableNameOfField + "." + keyName, strForSort.length(), " ");
			}

			// handle sort order
			SortOrder newSortOrder = sortOrder;
			if (aPageDirection.equals("prev")) {
				newSortOrder = SortOrder.ReverseOrder(sortOrder);
			} 
			if (strOrder.isEmpty() == false) strOrder += ", ";
			strOrder += tableNameOfField + "." + keyName + " " + SortOrder.AsString(newSortOrder);
		}

		if (concatFieldName.isEmpty() == false && aKeyField.size() == 1) {
			if (Database.GetDbType(aConn) == DbType.MYSQL) {
				concatFieldName += ", ''";
			} else {
				concatFieldName += " || ''";
			}
		}

		// for mysql only
		if (concatFieldName.isEmpty() == false) {
			if (Database.GetDbType(aConn) == DbType.MYSQL) {
				concatFieldName = "concat(" + concatFieldName + ")";
			}
		}

		SortOrder primarySortOrder = aSortOrder.get(0);
		String strWhereExpression = GetPagingCondition(concatFieldName, concatFieldValue, primarySortOrder, aPageDirection);

		List<Object> result = new CopyOnWriteArrayList<>();
		result.add(strWhereExpression);
		result.add(strOrder);
		result.add(concatFieldValue.trim().isEmpty());
		return result;
	}

	public static String GetPagingCondition(String aConcatField, String aConcatValue, SortOrder aDisplayOrder, String aPageDirection) throws Exception {
		String whereClause = "";
		//String sortOrder;

		// reverse the direction if display by descending order
		if (aDisplayOrder == SortOrder.DSC) {
			if (aPageDirection != null && (aPageDirection.equals("next") || aPageDirection.equals("seek"))) { 
				aPageDirection = "prev";
			} else {
				aPageDirection = "next";
			}
		}

		// sql to section the result set
		if (aPageDirection.equals("next") || aPageDirection.equals("seek")) {
			if (aConcatValue != null && aConcatValue.isEmpty() == false) {
				whereClause += "lower(" + aConcatField + ") >= '" + aConcatValue + "'";
			}
		} else if (aPageDirection.equals("prev")) {
			if (aConcatValue != null && aConcatValue.isEmpty() == false) {
				whereClause += "lower(" + aConcatField + ") <= '" + aConcatValue + "'";
			}
		}


		return whereClause;
	}

	public static <Ty extends Clasz<?>> FetchStatus FetchBySection(Connection aConn, Class<Ty> aClass2Fetch, String aKeyField, String aKeyValue
	, SortOrder aSortOrder, Multimap<String, Record> whereBox, FieldObjectBox<Ty> aFobResult
	, String aPageDirection, int aPageSize, Callback2ProcessResultSet aCallback) throws Exception {

		List<String> keyField = new CopyOnWriteArrayList<>();
		keyField.add(aKeyField);

		List<String> keyValue = new CopyOnWriteArrayList<>();
		keyValue.add(aKeyValue); 

		List<SortOrder> orderList = new CopyOnWriteArrayList<>();
		orderList.add(aSortOrder);

		Ty clasz = ObjectBase.CreateObjectTransient(aConn, aClass2Fetch); // use transient to avoid re-creating the table when run multiple time
		List<Ty> claszList = new CopyOnWriteArrayList<>();
		claszList.add(clasz);

		List<Clasz<?>> deepClaszList = new CopyOnWriteArrayList<>();
		List<String> deepFieldNameList = new CopyOnWriteArrayList<>();

		return Clasz.FetchBySection(aConn, claszList, keyField, keyValue, orderList, whereBox, aFobResult, aPageDirection, aPageSize, deepClaszList, deepFieldNameList, aCallback);
	}

	public static <Ty extends Clasz<?>> FetchStatus FetchBySection(Connection aConn, Class<Ty> aClass2Fetch, List<String> aKeyField, List<String> aKeyValue
	, List<SortOrder> aSortOrder , Record aWhereRec, FieldObjectBox<Ty> aFobResult, String aPageDirection, int aPageSize) throws Exception {
		Multimap<String, Record> whereBox = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
		String tableName = Clasz.CreateTableName(aClass2Fetch);
		whereBox.put(tableName, aWhereRec);

		List<Ty> claszList = new CopyOnWriteArrayList<>();
		Ty clasz = ObjectBase.CreateObjectTransient(aConn, aClass2Fetch); // use transient to avoid re-creating the table when run multiple time
		// for(String eachFieldName: aKeyField) claszList.add(clasz);
		for(int cntr = 0; cntr < aKeyField.size(); cntr++) claszList.add(clasz);

		List<Clasz<?>> deepClaszList = new CopyOnWriteArrayList<>();
		List<String> deepFieldNameList = new CopyOnWriteArrayList<>();

		return Clasz.FetchBySection(aConn, claszList, aKeyField, aKeyValue, aSortOrder, whereBox, aFobResult, aPageDirection, aPageSize, deepClaszList, deepFieldNameList, null);
	}

	public static <Ty extends Clasz<?>> FetchStatus FetchBySection(Connection aConn, Class<Ty> aClass2Fetch, List<String> aKeyField, List<String> aKeyValue
	, List<SortOrder> aSortOrder, Multimap<String, Record> aWhereBox, FieldObjectBox<Ty> aFobResult, String aPageDirection
	, int aPageSize, Callback2ProcessResultSet aCallback) throws Exception {

		List<Ty> claszList = new CopyOnWriteArrayList<>();
		Ty clasz = ObjectBase.CreateObjectTransient(aConn, aClass2Fetch); // use transient to avoid re-creating the table when run multiple time
		// for(String eachFieldName: aKeyField) claszList.add(clasz);
		for(int cntr = 0; cntr < aKeyField.size(); cntr++) claszList.add(clasz);

		List<Clasz<?>> deepClaszList = new CopyOnWriteArrayList<>();
		List<String> deepFieldNameList = new CopyOnWriteArrayList<>();

		return Clasz.FetchBySection(aConn, claszList, aKeyField, aKeyValue, aSortOrder, aWhereBox, aFobResult, aPageDirection, aPageSize, deepClaszList, deepFieldNameList, null);
	}

	public static <Ty extends Clasz<?>> FetchStatus FetchBySection(Connection aConn, List<Ty> aKeyClasz, List<String> aKeyField, List<String> aKeyValue, List<SortOrder> aSortOrder
	, Multimap<String, Record> aWhereBox, FieldObjectBox<Ty> aFobResult, String aPageDirection, int aPageSize
	, List<Clasz<?>> aClasz2Link, List<String> aField2Link) throws Exception {
		return FetchBySectionWithDeepParentLink(aConn, aKeyClasz, aKeyField, aKeyValue, aSortOrder, aWhereBox, aFobResult, aPageDirection, aPageSize, aClasz2Link, aField2Link, null);
	}

	public static <Ty extends Clasz<?>> FetchStatus FetchBySection(Connection aConn, List<Ty> aKeyClasz, List<String> aKeyField, List<String> aKeyValue, List<SortOrder> aSortOrder
	, Multimap<String, Record> aWhereBox, FieldObjectBox<Ty> aFobResult, String aPageDirection, int aPageSize
	, List<Clasz<?>> aClasz2Link, List<String> aField2Link, Callback2ProcessResultSet aCallback) throws Exception {
		return FetchBySectionWithDeepParentLink(aConn, aKeyClasz, aKeyField, aKeyValue, aSortOrder, aWhereBox, aFobResult, aPageDirection, aPageSize, aClasz2Link, aField2Link, aCallback);
	}

	@SuppressWarnings("unchecked")
	private static <Ty extends Clasz<?>> FetchStatus FetchBySectionWithDeepParentLink(Connection aConn
	, List<Ty> aKeyClasz, List<String> aKeyField, List<String> aKeyValue, List<SortOrder> aSortOrder
	, Multimap<String, Record> aWhereBox, FieldObjectBox<Ty> aFobResult, String aPageDirection, int aPageSize
	, List<Clasz<?>> aClasz2Link, List<String> aField2Link
	, Callback2ProcessResultSet aCallback) throws Exception {

		int result = 0;
		FetchStatus fetchStatus;

		if (aKeyClasz.size() != aKeyField.size() && aKeyField.size() != aKeyValue.size()) {
			throw new Hinderance("When FetchBySectionWithDeepParentLink, its total key clasz, key field and key value must be the same!");
		}

		Class<?> clasz2Fetch;
		List<Field> deepArrBindField = new CopyOnWriteArrayList<>();
		String deepFromStr = "";
		String deepWhereStr = "";
		int lastClaszIndex = aClasz2Link.size() - 1;
		if (lastClaszIndex < 0) {
			// no deep link is needed, do nothing so all deepArrField, deepFromStr etc variable is empty
			clasz2Fetch = aKeyClasz.get(0).getClass();
		} else {
			List<Object> listResult = Link2DeepParentClause(aConn, aClasz2Link, aField2Link);
			deepArrBindField = (List<Field>) listResult.get(0);
			deepFromStr = (String) listResult.get(1);
			deepWhereStr = (String) listResult.get(2);

			// get the leaf clasz fob member object to fetch
			Clasz<?> parentClasz = aClasz2Link.get(lastClaszIndex);
			String parentFobName = aField2Link.get(lastClaszIndex);
			FieldObjectBox<Ty> fob2Fetch = (FieldObjectBox<Ty>) parentClasz.getField(parentFobName);
			clasz2Fetch = fob2Fetch.getMemberClass();
		}

		// handle additional where clause
		String tableName = Clasz.CreateTableName(clasz2Fetch);
		List<Object> fetchObject = Table.GetSqlAndBindArray(aConn, tableName, null, aWhereBox);
		String fullSqlStr = (String) fetchObject.get(0);
		List<Field> whereBindList = (List<Field>) fetchObject.get(1);

		// get sql objects for pagination clause
		List<Object> keyPageObject = Clasz.SqlForPaging(aConn, aKeyClasz, aKeyField, aKeyValue, aSortOrder, aPageDirection); 
		String keyFromStr = ((String) keyPageObject.get(0));
		String keyWhereExpression = ((String) keyPageObject.get(1));
		String strSortOrder = ((String) keyPageObject.get(2));

		// handle from clause
		fullSqlStr = InsertFrom2Sql(fullSqlStr, deepFromStr);
		fullSqlStr = InsertFrom2Sql(fullSqlStr, keyFromStr);

		// handle where clause
		String whereClause = "";
		if (deepWhereStr.isEmpty() == false) {
			if (whereClause.isEmpty() == false) whereClause += " and ";
			whereClause += "(" + deepWhereStr + ")";
		}
		if (keyWhereExpression.isEmpty() == false) {
			if (whereClause.isEmpty() == false) whereClause += " and ";
			whereClause += "(" + keyWhereExpression + ")";
		}
		if (whereClause.isEmpty() == false) {
			whereClause = "(" + whereClause + ")";
		}

		// handle order by clause
		String sortClause = strSortOrder;
			
		// handle where clause
		if (whereClause.isEmpty() == false) {
			if (fullSqlStr.contains("where") == false) {
				fullSqlStr += " where";
			} else {
				fullSqlStr += " and";
			}
			fullSqlStr += " " + whereClause;
		}

		// handle order by clause
		if (sortClause.isEmpty() == false) {
			fullSqlStr += " order by " + sortClause.trim();
		}

		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			stmt = aConn.prepareStatement(fullSqlStr);
			Database.SetStmtValue(aConn, stmt, whereBindList);
			Database.SetStmtValue(aConn, stmt, deepArrBindField, whereBindList.size()); // add field binding for deep clasz linking
			rs = stmt.executeQuery();
			while (rs.next()) {
				if (aCallback == null) {
					Ty clasz = (Ty) ObjectBase.CreateObjectFromAnyClass(aConn, clasz2Fetch);
					clasz.populateObject(aConn, rs, false);
					aFobResult.addValueObject(clasz);
					result++;
				} else {
					if (aCallback.processResultSet(aConn, rs)) {
						result++;
					}
				}
				if (result >= aPageSize) break;
			}
		} catch(Exception ex) {
			throw new Hinderance(ex, "Fail Clasz.FetchBySection: " + fullSqlStr);
		} finally {
			fetchStatus = FetchStatus.EOF;
			if (rs != null) {
				rs.close();
			}
			if (stmt != null) {
				stmt.close();
			}
		}

		// FOB must got meta object, if user not set it, set here
		if (aFobResult.getMetaObj() == null) {
			Ty metaObj = (Ty) ObjectBase.CreateObjectFromAnyClass(aConn, clasz2Fetch);
			aFobResult.setMetaObj(metaObj);
			aFobResult.clearAllSortKey();
		}

		// sort according to user's order, sql ordering changes according to "prev" and "next" page direction
		for(int cntrField = 0; cntrField < aKeyField.size(); cntrField++) {
			String sortField = aKeyField.get(cntrField);
			aFobResult.getMetaObj().getField(sortField).setSortKeyNo(cntrField);
			aFobResult.getMetaObj().getField(sortField).setSortKey(true);
			aFobResult.getMetaObj().getField(sortField).setSortOrder(aSortOrder.get(cntrField));
		}
		if (aKeyField.size() > 0) aFobResult.sort();

		aFobResult.setFetchStatus(fetchStatus);
		return(fetchStatus);
	}


	// no aConnection, so make sure this call is for Fob with members
	public static <Tf extends Clasz<?>> Tf FetchLatestMemberFromFob(Clasz<?> aThis, String aBoxName, String aLatestField, Class<Tf> aType) throws Exception {
		return aType.cast(aThis.fetchLatestMemberFromFob(aBoxName, aLatestField, aType));
	}

	public <Tf extends Clasz<?>> Tf fetchLatestMemberFromFob(String aBoxName, String aLatestField, Class<Tf> aType) throws Exception {
		return fetchLatestMemberFromFob(aBoxName, aLatestField, aType, null);
	}

	// no aConnection, so make sure this call is for Fob with members
	public <Tf extends Clasz<?>> Tf fetchLatestMemberFromFob(String aFobFieldName, String aMemberTimeField, Class<Tf> aMemberType, Callback2ProcessObject aCallback) throws Exception {
		LambdaGeneric<Clasz<?>> lambdaObject = new LambdaGeneric<>(aMemberType.cast(null));
		final String memberTimeField = aMemberTimeField;
		this.getFieldObjectBox(aFobFieldName).resetIterator();
		while (this.getFieldObjectBox(aFobFieldName).hasNext()) {
			Clasz<?> currentClasz = this.getFieldObjectBox(aFobFieldName).getNext();
			Clasz<?> latestClasz = (Clasz<?>) lambdaObject.getValue();
			if (latestClasz == null) {
				if (aCallback != null) {
					if (aCallback.processObject(currentClasz) == true) {
						lambdaObject.setValue(currentClasz);
					}
				} else {
					lambdaObject.setValue(currentClasz);
				}
			} else {
				try {
					if (latestClasz.getField(memberTimeField) != null) {
						DateTime latestDate;
						DateTime currentDate;
						if (latestClasz.getField(memberTimeField) instanceof FieldDateTime) {
							latestDate = latestClasz.getValueDateTime(memberTimeField);	
							currentDate = currentClasz.getValueDateTime(memberTimeField);
						} else {
							latestDate = latestClasz.getValueDate(memberTimeField);	
							currentDate = currentClasz.getValueDate(memberTimeField);
						}
						if (latestDate.isBefore(currentDate)) {
							if (aCallback != null) {
								if (aCallback.processObject(currentClasz) == true) {
									lambdaObject.setValue(currentClasz);
								}
							} else {
								lambdaObject.setValue(currentClasz);
							}
						}
					}
				} catch(Exception ex) {
					throw new Hinderance(ex, "Fail, exception thrown in Clasz.fetchLatest method.");
				}
			}
		}

		return aMemberType.cast(lambdaObject.getValue());
	}

	public static String GetClaszIdentifier(Clasz<?> aClasz) throws Exception {
		return "clasz: " + aClasz.getClass().getSimpleName() + ", oid: " + aClasz.getObjectId();
	}

}