package biz.shujutech.db.relational;

import biz.shujutech.base.App;
import biz.shujutech.base.Connection;
import biz.shujutech.base.DateAndTime;
import biz.shujutech.base.Hinderance;
import biz.shujutech.db.object.Clasz;
import biz.shujutech.reflect.AttribIndex;
import biz.shujutech.reflect.ReflectField;
import biz.shujutech.reflect.ReflectIndex;
import biz.shujutech.util.Generic;
import com.google.common.collect.Multimap;
import java.io.ByteArrayInputStream;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.commons.lang3.exception.ExceptionUtils;


public class Database extends BaseDb {
	private static final int COMPARE_KEY_OBJECT_INDEX = 1; 
	private static final int COMPARE_KEY_TABLE_INDEX = 2;

	public enum DbType {
		ORACLE, MYSQL, MARIADB, POSTGRESQL
	}

	public Database() {
		super();
	}

	public static void createIndex(Connection aConn, Table aTable) throws Exception {
		CreateIndex(aConn, aTable, false);
	}

	public static void createUniqueIndex(Connection aConn, Table aTable) throws Exception {
		CreateIndex(aConn, aTable, true);
	}

	private static class FieldIndexComparator implements Comparator<Field> {
		private int compareType = 0;
		public FieldIndexComparator(int aCompareType) {
			this.compareType = aCompareType;
		}

		@Override
		public int compare(Field aField1, Field aField2) {
			int result = 0;
			//Field sp1 = (Field) aField1;
			//Field sp2 = (Field) aField2;
			Field sp1 = aField1;
			Field sp2 = aField2;
			if (this.compareType == COMPARE_KEY_OBJECT_INDEX) {
				result = (sp1.getObjectKeyNo() < sp2.getObjectKeyNo()) ? -1: (sp1.getObjectKeyNo() > sp2.getObjectKeyNo()) ? 1:0 ;
			} else if (this.compareType == COMPARE_KEY_TABLE_INDEX) {
				result = (sp1.getIndexKeyNo() < sp2.getIndexKeyNo()) ? -1: (sp1.getIndexKeyNo() > sp2.getIndexKeyNo()) ? 1:0 ;
			} else {
				//App.logEror("When comparing fields, must specify if the field to be compare is for indexing object or table!");
				throw new RuntimeException("When comparing fields, must specify if the field to be compare is for indexing object or table!");
			}
			return(result);
		}
	}

	public static void CreateIndexes(Connection aConn, String aTableName, Record aRecord) throws Exception { // y not use meta record
		List<String> doneIndexName = new CopyOnWriteArrayList<>();
		List<Field> groupedIndexField = new CopyOnWriteArrayList<>();
		String targetedIndexName = "";
		do { // loop thru each field
			targetedIndexName = "";
			for (Field eachField : aRecord.getFieldBox().values()) {
				if (eachField.isInline() == false && eachField.isFlatten() == false) { // inline field and ignore flatten field cannot be index
					for (AttribIndex eachAttrib : eachField.indexes) { // one field may contain multiple index attributes
						String idxName = eachAttrib.indexName;
						if (idxName.isEmpty() == false) {
							if (doneIndexName.contains(idxName) == false) { // index never been process before
								if (targetedIndexName.isEmpty()) targetedIndexName = idxName; // found a new index, place it as target
								if (targetedIndexName.equals(idxName)) {
									Field field2Index = Field.CreateField(eachField);
									field2Index.setIndexKey(true);
									field2Index.setIndexKeyNo(eachAttrib.indexNo);
									field2Index.setIndexKeyOrder(eachAttrib.indexOrder);
									field2Index.setUniqueKey(eachAttrib.isUnique);
									if (eachAttrib.ignoreCase) {
										if (eachField.getDbFieldType() != FieldType.STRING && eachField.getDbFieldType() != FieldType.HTML) {
											throw new Hinderance("ignoreCase index is only supported on STRING/HTML fields, field: " + eachField.getDbFieldName() + ", type: " + eachField.getDbFieldType());
										}
										field2Index.setDbFieldName(GeneratedLowerColumnName(eachField.getDbFieldName()));
									}
									groupedIndexField.add(field2Index);
								}
							}
						} else {
							throw new Hinderance("Index name in annotation must be specify, error at: " + aTableName + "." + eachField.getDbFieldName());
						}
					}
				} 
			}

			if (targetedIndexName.isEmpty() == false) {
				Database.CreateIndex(aConn, aTableName, targetedIndexName, groupedIndexField); // create the index
				doneIndexName.add(targetedIndexName);
			}
			groupedIndexField.clear();
		} while(targetedIndexName.isEmpty() == false);
	}

	public static void CreateIndex(Connection aConn, String aTableName, List<Field> aField2Index) throws Exception {
		CreateIndex(aConn, aTableName, "", aField2Index);
	}

	public static void CreateIndex(Connection aConn, String aTableName, String indexName, List<Field> aField2Index) throws Exception {
		String unique = "";
		for (Field eachField : aField2Index) {
			if (eachField.isUniqueKey()) {
				unique = "unique ";
				break;
			}
		}

		if (indexName == null || indexName.isEmpty()) {
			indexName = Table.GetIndexName(aTableName, aField2Index);
		}

		String strSql = "create " + unique  + "index " + indexName + " on " + aTableName;
		int totalField = 0;
		for (Field eachField : aField2Index) {
			if (totalField == 0) {  
				strSql += "(";
			} else {
				strSql += ", ";
			}
			String keyOrder = SortOrder.AsString(eachField.getIndexKeyOrder());
			strSql += eachField.getDbFieldName() + " " + keyOrder; 
			totalField++;
		}
		if (totalField != 0) strSql += ")";
		App.logDebg(Database.class, "Creating index: " + strSql);
		ExecuteDdl(aConn, strSql);
	}

	public static void CreateIndex(Connection aConn, Table aTable) throws Exception {
		CreateIndex(aConn, aTable, false);
	}

	public static void CreateUniqueIndex(Connection aConn, Table aTable) throws Exception {
		CreateIndex(aConn, aTable, true);
	}

	public static void CreateIndex(Connection aConn, Table aTable, boolean aUnique) throws Exception {
		String unique = "";
		String indexName = aTable.getIndexName();

		// get the fields in the table according to its IndexKeyNo first
		List<Field> aryField = new CopyOnWriteArrayList<>();
		for (Field eachField : aTable.getMetaRec().getFieldBox().values()) {
			aryField.add(eachField);
		}
		Collections.sort(aryField, new FieldIndexComparator(COMPARE_KEY_OBJECT_INDEX));

		if (aUnique) {
			unique = "unique ";
			//indexName = aTable.getUniqueIndexName();
			indexName = indexName + "_unq";
		} 
		String strSql = "create " + unique  + "index " + indexName + " on " + aTable.getTableName();
		int totalField = 0;
		for (Field eachField : aryField) {
			boolean isKey;
			if (aUnique) {
				isKey = eachField.isUniqueKey();
			} else {
				isKey = eachField.isObjectKey();
			}
			if (isKey) {
				if (totalField == 0) {  
					strSql += "(";
				} else {
					strSql += ", ";
				}
				String keyOrder = SortOrder.AsString(eachField.getObjectKeyOrder());
				strSql += eachField.getDbFieldName() + " " + keyOrder; 
				totalField++;
			}
		}
		if (totalField != 0) {
			strSql += ")";
		}
		//App.logInfo(Database.class, "ddl_create_index: " + strSql);
		ExecuteDdl(aConn, strSql);
	}

	public void createTable(Connection aConn, Table aTable) throws Exception {
		CreateTable(aConn, aTable);
		aTable.setDb(this);
	}

	public static void CreateTable(Connection aConn, Table aTable) throws Exception {
		String strSql = "create table " + aTable.getTableName();
		int totalField = 0;
		for (Field eachField : aTable.getMetaRec().getFieldBox().values()) {
			if (eachField.getDbFieldType() == FieldType.OBJECT) {
				continue;
			} // ignore and don't do anything to OBJECT field types
			if (totalField == 0) {  
				strSql += "(";
			} else {
				strSql += ", ";
			}
			strSql += eachField.getDbFieldName(); 
			strSql += " " + DdlFieldType(aConn, eachField.getDbFieldType(), eachField.getFieldSize());
			if (eachField.getDefaultValue().isEmpty() == false) {
				strSql += " default " + eachField.getDefaultValue();
			}
			totalField++;
		}
		// For each field that has an ignoreCase index, append a generated lowercase column
		for (Field eachField : aTable.getMetaRec().getFieldBox().values()) {
			if (eachField.getDbFieldType() == FieldType.OBJECT) continue;
			if (eachField.isInline() || eachField.isFlatten()) continue;
			if (IsIgnoreCase(aTable.getTableName(), eachField)) {
				if (eachField.getDbFieldType() != FieldType.STRING && eachField.getDbFieldType() != FieldType.HTML) {
					throw new Hinderance("ignoreCase index is only supported on STRING/HTML fields, field: " + eachField.getDbFieldName() + ", type: " + eachField.getDbFieldType());
				}
				if (totalField == 0) {
					strSql += "(";
				} else {
					strSql += ", ";
				}
				strSql += GeneratedLowerColumnDdl(aConn, eachField);
				totalField++;
			}
		}
		if (totalField != 0) {
			strSql += ")";
		}
		strSql += CreateTablePostfix(aConn);
		//App.logInfo(Database.class, "ddl_create_table: " + strSql);
		ExecuteDdl(aConn, strSql);
	}

	public static String GeneratedLowerColumnName(String aFieldName) {
		return aFieldName + "_lc";
	}

	/**
	 * Maximum number of (tableName, fieldName) entries the ignoreCase fast-lookup
	 * registry will track. If a project declares more ignoreCase fields than this,
	 * an error is logged once and {@link #IsIgnoreCase(String, Field)} falls back
	 * to a slow per-call classpath scan instead of failing.
	 */
	public static final int MAX_IGNORE_CASE_REGISTRY_SIZE = 1024;

	private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> IGNORE_CASE_REGISTRY = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.concurrent.atomic.AtomicBoolean IGNORE_CASE_REGISTRY_OVERFLOW = new java.util.concurrent.atomic.AtomicBoolean(false);

	private static String BuildIgnoreCaseKey(String aTableName, String aDbFieldName) {
		return aTableName + "::" + aDbFieldName;
	}

	/**
	 * Records that the given (tableName, dbFieldName) pair has
	 * {@code ignoreCase=true} on at least one of its {@code @ReflectIndex}
	 * annotations. Should be called once per ignoreCase field as the class
	 * metadata is parsed. If the registry exceeds {@link #MAX_IGNORE_CASE_REGISTRY_SIZE}
	 * entries, the overflow flag is set and an error is logged once; further
	 * lookups will fall back to the slow path.
	 */
	public static void RegisterIgnoreCase(String aTableName, String aDbFieldName) {
		if (aTableName == null || aDbFieldName == null) return;
		if (IGNORE_CASE_REGISTRY.size() >= MAX_IGNORE_CASE_REGISTRY_SIZE) {
			if (IGNORE_CASE_REGISTRY_OVERFLOW.compareAndSet(false, true)) {
				App.logEror(Database.class, "ignoreCase registry exceeded MAX_IGNORE_CASE_REGISTRY_SIZE=" + MAX_IGNORE_CASE_REGISTRY_SIZE + "; subsequent IsIgnoreCase lookups will fall back to slow classpath scan");
			}
			return;
		}
		IGNORE_CASE_REGISTRY.put(BuildIgnoreCaseKey(aTableName, aDbFieldName), Boolean.TRUE);
	}

	/**
	 * Determines if the given field is annotated with {@code ignoreCase=true} on
	 * any of its {@code @ReflectIndex} entries. The passed-in {@code aField} is
	 * only used to obtain its db field name; its own {@code indexes} list is NOT
	 * consulted (the canonical {@link Clasz} metadata is the source of truth).
	 * <p>
	 * Fast path: if the registry has not overflowed, an O(1) lookup against the
	 * map populated when the class metadata was parsed.
	 * <p>
	 * Slow fallback: when the registry has overflowed, resolves the {@link Clasz}
	 * subclass from {@code aTableName} (by inverting {@link #Java2DbTableName} and
	 * scanning the classpath) and walks its {@code @ReflectField} annotations to
	 * find the matching {@code @ReflectIndex(ignoreCase=true)}.
	 */
	public static boolean IsIgnoreCase(String aTableName, Field aField) throws Exception {
		if (aTableName == null || aField == null) return false;
		String dbFieldName = aField.getDbFieldName();
		if (dbFieldName == null) return false;
		if (IGNORE_CASE_REGISTRY_OVERFLOW.get() == false) {
			return IGNORE_CASE_REGISTRY.containsKey(BuildIgnoreCaseKey(aTableName, dbFieldName));
		}
		return IsIgnoreCaseSlow(aTableName, dbFieldName);
	}

	private static boolean IsIgnoreCaseSlow(String aTableName, String aDbFieldName) throws Exception {
		Class<? extends Clasz<?>> claszClass = Clasz.GetClaszByTableName(aTableName);
		if (claszClass == null) return false;
		Class<?> walker = claszClass;
		while (walker != null && walker != Object.class) {
			for (java.lang.reflect.Field reflectField : walker.getDeclaredFields()) {
				ReflectField annotation = reflectField.getAnnotation(ReflectField.class);
				if (annotation == null) continue;
				String reflectDbFieldName = Clasz.CreateDbFieldName(reflectField.getName(), "");
				if (reflectDbFieldName.equals(aDbFieldName)) {
					for (ReflectIndex idx : annotation.indexes()) {
						if (idx.ignoreCase()) return true;
					}
					return false;
				}
			}
			walker = walker.getSuperclass();
		}
		return false;
	}

	public static String GeneratedLowerColumnDdl(Connection aConn, Field aField) throws Exception {
		String fieldName = aField.getDbFieldName();
		String lcName = GeneratedLowerColumnName(fieldName);
		int size = aField.getFieldSize();
		DbType dbType = GetDbType(aConn);
		String result;
		if (dbType == DbType.POSTGRESQL) {
			result = lcName + " varchar(" + size + ") generated always as (lower(" + fieldName + ")) stored";
		} else if (dbType == DbType.MYSQL) {
			result = lcName + " varchar(" + size + ") generated always as (lower(" + fieldName + ")) stored";
		} else if (dbType == DbType.MARIADB) {
			result = lcName + " varchar(" + size + ") as (lower(" + fieldName + ")) persistent";
		} else if (dbType == DbType.ORACLE) {
			result = lcName + " varchar2(" + size + ") generated always as (lower(" + fieldName + "))";
		} else {
			throw new Hinderance("Unsupported db type for ignoreCase generated column: " + dbType);
		}
		return(result);
	}

	public static void AlterTableAddIgnoreCaseColumns(Connection aConn, Table aTable) throws Exception {
		Table existingTable = new Table(aTable.getTableName());
		existingTable.initMeta(aConn);
		for (Field eachField : aTable.getMetaRec().getFieldBox().values()) {
			if (eachField.getDbFieldType() == FieldType.OBJECT) continue;
			if (eachField.getDbFieldType() == FieldType.OBJECTBOX) continue;
			if (eachField.isInline() || eachField.isFlatten()) continue;
			if (IsIgnoreCase(aTable.getTableName(), eachField) == false) continue;
			if (eachField.getDbFieldType() != FieldType.STRING && eachField.getDbFieldType() != FieldType.HTML) {
				throw new Hinderance("ignoreCase index is only supported on STRING/HTML fields, field: " + eachField.getDbFieldName() + ", type: " + eachField.getDbFieldType());
			}
			String lcName = GeneratedLowerColumnName(eachField.getDbFieldName());
			if (existingTable.fieldExist(lcName)) continue;
			String alterSql = "alter table " + aTable.getTableName() + " add " + GeneratedLowerColumnDdl(aConn, eachField);
			App.logInfo(Database.class, "Adding generated lowercase column: " + alterSql);
			ExecuteDdl(aConn, alterSql);
		}
	}

	public void createPrimaryKey(Table aTable) throws Exception {
		Connection conn = this.connPool.getConnection();
		try {
			CreatePrimaryKey(conn, aTable);
		} finally {
			if (conn != null) {
				this.connPool.freeConnection(conn);
			}
		}		
	}

	public static List<Field> GetPkInSeq(Table aTable) throws Exception {
		List<Field> pkOrder = new CopyOnWriteArrayList<>();
		for (Field eachField : aTable.getMetaRec().getFieldBox().values()) {
			int insertAt = 0;
			for(int cntr = 0; cntr < pkOrder.size(); cntr++) {
				Field currField = pkOrder.get(cntr);
				if (currField.getPrimaryKeySeq() >= eachField.getPrimaryKeySeq()) {
					insertAt = cntr;
					break;
				} else {
					insertAt = cntr + 1;
				}
			}
			pkOrder.add(insertAt, eachField);
		}
		return(pkOrder);
	}

	public static void CreatePrimaryKey(Connection aConn, Table aTable) throws Exception {
		String strSql = "alter table " + aTable.getTableName() + " add primary key ";
		int totalField = 0;
		//for (Field eachField : aTable.getMetaRec().getFieldBox().values()) {
		for (Field eachField : GetPkInSeq(aTable)) {
			if (eachField.isPrimaryKey()) {
				if (totalField == 0) {  
					strSql += "(";
				} else {
					strSql += ", ";
				}
				strSql += eachField.getDbFieldName(); 
				totalField++;
			}
		}
		if (totalField != 0) {
			strSql += ")";
		}
		ExecuteDdl(aConn, strSql);
	}

	public void createSequence(String aSeqName) throws Exception {
		Connection conn = this.connPool.getConnection();
		try {
			createSequence(aSeqName);
		} finally {
			if (conn != null) {
				this.connPool.freeConnection(conn);
			}
		}		
	}

	public static void CreateSequence(Connection aConn, String aSeqName) throws Exception {
		if (GetDbType(aConn) == DbType.MYSQL || GetDbType(aConn) == DbType.MARIADB || GetDbType(aConn) == DbType.ORACLE || GetDbType(aConn) == DbType.POSTGRESQL) {
			String sqlCreate = "create table " + aSeqName + " (id bigint not null)";
			ExecuteDdl(aConn, sqlCreate);
			String sqlInsert = "insert into " + aSeqName + " values(0)";
			ExecuteDdl(aConn, sqlInsert);
		}
	}

	public static long GetNextSequence(Connection aConn, String aSeqName) throws Exception {
		long result = -1;
		if (GetDbType(aConn) == DbType.MYSQL || GetDbType(aConn) == DbType.MARIADB || GetDbType(aConn) == DbType.ORACLE || GetDbType(aConn) == DbType.POSTGRESQL) {
			String sqlUpdate = "update " + aSeqName + " set id = last_insert_id(id + 1)";
			String sqlSelect = "select last_insert_id() next_seq";
			if (GetDbType(aConn) == DbType.POSTGRESQL)  {
				sqlUpdate = "update " + aSeqName + " set id = id + 1";
				sqlSelect = "select id next_seq from " + aSeqName;
			}
			ExecuteDdl(aConn, sqlUpdate);

			PreparedStatement stmt = null;
			ResultSet rset = null;
			try {
				//App.logDebg(sqlSelect);
				stmt = aConn.prepareStatement(sqlSelect);
				rset = stmt.executeQuery();
				if (rset.next()) { 
					result = rset.getLong("next_seq");
				} else {
					throw new Hinderance("Fail to get the next sequence for sequence: " + aSeqName.toUpperCase());
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
		return(result);
	}


	public void executeDdl(String strSql) throws Exception {
		Connection conn = this.connPool.getConnection();
		try {
			ExecuteDdl(conn, strSql);
		} finally {
			if (conn != null) {
				this.connPool.freeConnection(conn);
			}
		}		
	}

	public static void ExecuteDdl(Connection aConn, String strSql) throws Exception {
		PreparedStatement stmt = null;
		try {
			stmt = aConn.prepareStatement(strSql);
			stmt.executeUpdate();
			//App.logInfo(Database.class, "ddl_execute: " + strSql);
		} catch(Exception ex) {
			throw new Hinderance(ex, "Fail sql ddl: " + strSql);
		} finally {
			if (stmt != null) {
				stmt.close();
			}
		}		
	}

	@Deprecated
	public boolean tableExist(String aName) throws Exception {
		boolean result = false;
		Connection conn = this.connPool.getConnection();
		try {
			result = TableExist(conn, aName);
		} finally {
			if (conn != null) {
				this.connPool.freeConnection(conn);
			}
		}		
		return(result);
	}

	public boolean tableExist(Connection aConn, String aName) throws Exception {
		return(TableExist(aConn, aName));
	}

	public static boolean TableExist(Connection conn, String aName) throws Exception {
		boolean result = false;
		DatabaseMetaData meta = conn.getMetaData();
		ResultSet res = meta.getTables(null, null, aName, new String[] {"TABLE"});
		while (res.next()) {
			String tableName = res.getString("TABLE_NAME");
			if (tableName.toLowerCase().trim().equals(aName.toLowerCase().trim())) {
				result = true;
				break;
			}
 	 	}
		return(result);
	}

	public String ddlFieldPk(boolean aOid) throws Exception {
		String result = "";
		if (this.getDbType() == DbType.MYSQL || this.getDbType() == DbType.MARIADB || this.getDbType() == DbType.ORACLE || this.getDbType() == DbType.POSTGRESQL) {
			if (aOid) {
				result = "not null auto_increment";
			} else {
				result = "not null";
			}
		} else {
			throw new Hinderance("Unknonwn database type, jdbc url: " + this.connPool.cpUrl);
		}
		return(result);
	}

	public String ddlPk(Table aTable) throws Exception {
		String result = "";
		if (this.getDbType() == DbType.MYSQL || this.getDbType() == DbType.MARIADB || this.getDbType() == DbType.ORACLE || this.getDbType() == DbType.POSTGRESQL) {
			result = "primary key (" + aTable.getPkName() + ")";
		} else {
			throw new Hinderance("Unknonwn database type, jdbc url: " + this.connPool.cpUrl);
		}
		return(result);
	}

	public static String DdlFieldType(Connection aConn, FieldType aType, int aSize) throws Exception {
		String result = "";
		int sizeInt = String.valueOf(Integer.MAX_VALUE).length();
		int sizeLong = String.valueOf(Long.MAX_VALUE).length();
		if (GetDbType(aConn) == DbType.ORACLE) {
			if (aType == FieldType.STRING) {
				result = "varchar2(" + aSize + ")";
			} else if (aType == FieldType.INTEGER) {

			} else {
				throw new Hinderance("Unsupported and unknown column type: " + aType);
			}
		} else if (GetDbType(aConn) == DbType.MYSQL || GetDbType(aConn) == DbType.MARIADB) {
			if (aType == FieldType.STRING) {
				result = "varchar(" + aSize + ")";
			} else if (aType == FieldType.HTML) {
				result = "varchar(" + aSize + ")";
			} else if (aType == FieldType.INTEGER) {
				result = "int(" + sizeInt + ")";
			} else if (aType == FieldType.BOOLEAN) {
				result = "tinyint(1)";
			} else if (aType == FieldType.ENCRYPT) {
				result = "blob";
			} else if (aType == FieldType.BASE64) {
				result = "mediumblob";
			} else if (aType == FieldType.DATETIME) {
				result = "timestamp null";
			} else if (aType == FieldType.DATE) {
				result = "date null";
			} else if (aType == FieldType.LONG) {
				result = "bigint(" + sizeLong + ")";
			} else if (aType == FieldType.FLOAT) {
				result = "float";
			} else {
				throw new Hinderance("Unsupported and unknown column type: " + aType);
			}
		} else if (GetDbType(aConn) == DbType.POSTGRESQL) {
			if (aType == FieldType.STRING) {
				result = "varchar(" + aSize + ")";
			} else if (aType == FieldType.HTML) {
				result = "varchar(" + aSize + ")";
			} else if (aType == FieldType.INTEGER) {
				result = "integer";
			} else if (aType == FieldType.BOOLEAN) {
				result = "boolean";
			} else if (aType == FieldType.ENCRYPT) {
				result = "bytea";
			} else if (aType == FieldType.BASE64) {
				result = "bytea";
			} else if (aType == FieldType.DATETIME) {
				result = "timestamptz null";
			} else if (aType == FieldType.DATE) {
				result = "date null";
			} else if (aType == FieldType.LONG) {
				result = "bigint";
			} else if (aType == FieldType.FLOAT) {
				result = "real";
			} else {
				throw new Hinderance("Unsupported and unknown column type: " + aType);
			}
		}
		return(result);
	}

	public static String CreateTablePostfix(Connection aConn) throws Exception {
		String result = "";
		if (GetDbType(aConn) == DbType.ORACLE) {
		} else if (GetDbType(aConn) == DbType.MYSQL || GetDbType(aConn) == DbType.MARIADB) {
			result = " engine = innodb";
		}
		return(result);
	}

	public DbType getDbType() throws Exception {
		DbType result;
		if (this.connPool.cpUrl.toLowerCase().contains("oracle")) {
			result = DbType.ORACLE;
		} else if (this.connPool.cpUrl.toLowerCase().contains("mariadb")) {
			result = DbType.MARIADB;
		} else if (this.connPool.cpUrl.toLowerCase().contains("mysql")) {
			result = DbType.MYSQL;
		} else if (this.connPool.cpUrl.toLowerCase().contains("postgresql")) {      
			result = DbType.POSTGRESQL;
		} else {
			throw new Hinderance("Fail to determine the database type for connection: " + this.connPool.cpUrl);
		}
		return(result);
	}

	public static DbType GetDbType(java.sql.Connection aConn) throws Exception {
		DbType result;

		DatabaseMetaData dmd = aConn.getMetaData();
		String urlMeta = dmd.getURL();
		String productName = "";
		try {
			productName = dmd.getDatabaseProductName();
		} catch (Exception ex) {
			productName = "";
		}
		String urlLc = urlMeta == null ? "" : urlMeta.toLowerCase();
		String prodLc = productName == null ? "" : productName.toLowerCase();
		if (urlLc.contains("oracle") || prodLc.contains("oracle")) {
			result = DbType.ORACLE;
		} else if (urlLc.contains("mariadb") || prodLc.contains("mariadb")) {
			result = DbType.MARIADB;
		} else if (urlLc.contains("mysql") || prodLc.contains("mysql")) {
			result = DbType.MYSQL;
		} else if (urlLc.contains("postgresql") || prodLc.contains("postgres")) {
			result = DbType.POSTGRESQL;
		} else {
			throw new Hinderance("Fail to determine the database type for connection: " + urlMeta);
		}
		return(result);
	}

	/*
	public FieldType java2DbFieldType(Class aType) throws Hinderance {
		FieldType result;
		if (aType == String.class) {
			result = FieldType.STRING;
		} else if (aType == Integer.class) {
			result = FieldType.INTEGER;
		} else if (aType == int.class) {
			result = FieldType.INTEGER;
		} else if (aType == DateTime.class) {
			result = FieldType.DATETIME;
		} else if (aType == Long.class) {
			result = FieldType.LONG;
		} else {
			throw new Hinderance("Unsupported and unknown column class: " + aType);
		}
		return(result);
	}
	*/

	public static String Java2DbFieldName(String dbFieldName) {
		return(Java2DbName(dbFieldName));
	}

	public static String Java2DbTableName(String dbFieldName) {
		String firstChar = (Character.valueOf(dbFieldName.charAt(0))).toString();
		String javaName = dbFieldName.replaceFirst(firstChar, firstChar.toLowerCase());
		return(Java2DbName(javaName));
	}

	/**
	 * Inverse of {@link #Java2DbTableName(String)} for the body portion
	 * (after the table name prefix is stripped). Converts a snake_case db name
	 * back to a CamelCase Java simple name (e.g. "leave_form" -> "LeaveForm").
	 */
	public static String Db2JavaTableName(String aDbName) {
		if (aDbName == null || aDbName.isEmpty()) return aDbName;
		StringBuilder sb = new StringBuilder();
		boolean upperNext = true;
		for (int i = 0; i < aDbName.length(); i++) {
			char c = aDbName.charAt(i);
			if (c == '_') {
				upperNext = true;
			} else if (upperNext) {
				sb.append(Character.toUpperCase(c));
				upperNext = false;
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static String Java2DbName(String aFieldName) {
		String result = aFieldName.substring(0, 1).toLowerCase();
		char[] charArray = aFieldName.substring(1).toCharArray();
		for (Character eachChar : charArray) {
			if (Character.isUpperCase(eachChar)) {
				result += "_" + Character.toLowerCase(eachChar);
			}
			else {
				result += eachChar;
			}
		}
		return(result);
	}

	public static FieldType JavaSqlType2FieldType(int aType) throws Hinderance {
		FieldType result;
		if (aType == Types.VARCHAR || aType == Types.CHAR || aType == Types.LONGNVARCHAR || aType == Types.LONGVARCHAR || aType == Types.LONGVARCHAR || aType == Types.NCHAR || aType == Types.NVARCHAR) {
			result = FieldType.STRING;
		} else if (aType == Types.INTEGER) {
			result = FieldType.INTEGER;
		} else if (aType == Types.FLOAT || aType == Types.REAL) {
			result = FieldType.FLOAT;
		} else if (aType == Types.DATE) {
			result = FieldType.DATE;
		} else if (aType == Types.TIMESTAMP) {
			result = FieldType.DATETIME;
		} else if (aType == Types.BIGINT) {
			result = FieldType.LONG;
		} else if (aType == Types.BIT) {
			result = FieldType.BOOLEAN;
		} else if (aType == Types.LONGVARBINARY) {
			result = FieldType.BASE64;
		} else if (aType == Types.BINARY) {
			result = FieldType.BASE64;
		} else {
			throw new Hinderance("Unsupported and unknown java sql type: " + aType);
		}
		return(result);
	}

	public static int FieldType2JavaSqlType(Field eachField) throws Exception {
		int result = Integer.MIN_VALUE;
		if (eachField.getDbFieldType() == FieldType.STRING) {
			result = Types.VARCHAR;
		} else	if (eachField.getDbFieldType() == FieldType.HTML) {
			result = Types.LONGVARCHAR;
		} else	if (eachField.getDbFieldType() == FieldType.DATETIME) {
			result = Types.TIMESTAMP_WITH_TIMEZONE;
		} else	if (eachField.getDbFieldType() == FieldType.DATE) {
			result = Types.DATE;
		} else	if (eachField.getDbFieldType() == FieldType.LONG) {
			result = Types.BIGINT;
		} else	if (eachField.getDbFieldType() == FieldType.INTEGER) {
			result = Types.INTEGER;
		} else	if (eachField.getDbFieldType() == FieldType.FLOAT) {
			result = Types.FLOAT;
		} else	if (eachField.getDbFieldType() == FieldType.BOOLEAN) {
			result = Types.BOOLEAN;
		} else	if (eachField.getDbFieldType() == FieldType.ENCRYPT) {
			result = Types.LONGVARBINARY;
		} else	if (eachField.getDbFieldType() == FieldType.BASE64) {
			result = Types.LONGVARBINARY;
		} else {
			throw new Hinderance("Unknown type for field: " + eachField.getDbFieldName().toUpperCase() + " when attempting to set it for SQL operation");
		}
		return(result);
	}

	/*
	public static void ShowSetStmt(List<Field> fieldArr) throws Exception {
		for(int cntrField = 0, cntrStmt = 0; cntrField < fieldArr.size(); cntrField++, cntrStmt++) {
			Field eachField = fieldArr.get(cntrField);
			if (eachField.getFieldType() == FieldType.OBJECT) { // ignore and don't do anything to OBJECT field types
				continue;
			} 
			if (eachField.getFieldType() == FieldType.OBJECTBOX) { // ignore and don't do anything to OBJECT field types
				continue;
			} 
			String valueDb = eachField.getValueStr();
			App.logInfo("Setting preparedstatement for col: " + eachField.getFieldName().toUpperCase() + ", value: " + valueDb);
		}
	}
	*/

	public static void SetStmtValue(Connection aConn, PreparedStatement stmt, List<Field> fieldArr) throws Exception {
		SetStmtValue(aConn, stmt, fieldArr, 0);
	}

	public static boolean IsSelectStmt(Statement aStmt) throws Exception {
		boolean result = false;
		String stmtSql = aStmt.toString().trim().toLowerCase();
		String pureSql;

		java.sql.Connection conn = aStmt.getConnection();
		GetDbType(conn);
		if (Database.GetDbType(conn) == DbType.MYSQL) {
			if (stmtSql.startsWith("com.mysql")) {
				int sqlStartAt = stmtSql.indexOf(" ");
				pureSql = stmtSql.substring(sqlStartAt).trim();
			} else {
				pureSql = stmtSql;
			}
		} else {
			pureSql = stmtSql;
		}

		if (pureSql.startsWith("select") == true) {
			result = true;
		}
		return(result);
	}

	public static void SetStmtValue(Connection aConn, PreparedStatement stmt, List<Field> fieldArr, int aStartIndex) throws Exception {
		for(int cntrField = 0, cntrStmt = aStartIndex; cntrField < fieldArr.size(); cntrField++) {
			Field eachField = fieldArr.get(cntrField);
			if (eachField != null) {
				if (eachField.getFormulaStr() == null || eachField.getFormulaStr().isEmpty() || eachField.getFormulaStr().contains("?")) {
					try {
						if (eachField.getDbFieldType() == FieldType.OBJECT || eachField.getDbFieldType() == FieldType.OBJECTBOX) { // ignore and don't do anything to OBJECT field types
							App.logWarn(Database.class, "Trying to SetStmtValue for not atomic field types, field: " + eachField.getDbFieldName());
							continue;
						} 

						Object valueDb = eachField.getValueObj(aConn);
						if (valueDb == null) {
							//if (stmt.toString().toLowerCase().startsWith("select") == false) {
							if (IsSelectStmt(stmt) == false) {
								stmt.setNull(++cntrStmt, FieldType2JavaSqlType(eachField));	// only do this for update or delete, for select can't do setNull, use is NULL with no ? parameter				
							}
						} else {
							if (eachField.getDbFieldType() == FieldType.STRING) {
								stmt.setString(++cntrStmt, eachField.getValueStr());
							} else	if (eachField.getDbFieldType() == FieldType.HTML) {
								stmt.setString(++cntrStmt, eachField.getValueStr());
							} else	if (eachField.getDbFieldType() == FieldType.DATETIME) {
								String dbDate = DateAndTime.FormatForJdbcTimestamp(((FieldDateTime) eachField).getValueDateTime());
								java.sql.Timestamp dateValue = java.sql.Timestamp.valueOf(dbDate); // format must be in "2005-04-06 09:01:10"
								stmt.setTimestamp(++cntrStmt, dateValue);
							} else	if (eachField.getDbFieldType() == FieldType.DATE) {
								String dbDate = DateAndTime.FormatForJdbcDate(((FieldDate) eachField).getValueDate());
								java.sql.Date dateValue = java.sql.Date.valueOf(dbDate); // format must be in "2005-04-06 09:01:10"
								stmt.setDate(++cntrStmt, dateValue);
							} else	if (eachField.getDbFieldType() == FieldType.LONG) {
								stmt.setLong(++cntrStmt, ((FieldLong) eachField).getValueLong());
							} else	if (eachField.getDbFieldType() == FieldType.INTEGER) {
								stmt.setInt(++cntrStmt, ((FieldInt) eachField).getValueInt());
							} else	if (eachField.getDbFieldType() == FieldType.FLOAT) {
								stmt.setFloat(++cntrStmt, ((FieldFloat) eachField).getValueFloat());
							} else	if (eachField.getDbFieldType() == FieldType.BOOLEAN) {
								stmt.setBoolean(++cntrStmt, ((FieldBoolean) eachField).getValueBoolean());
							} else	if (eachField.getDbFieldType() == FieldType.ENCRYPT) {
								//stmt.setString(++cntrStmt, (String) eachField.getValueObj());
								stmt.setBytes(++cntrStmt, ((String) eachField.getValueObj(aConn)).getBytes());
							} else	if (eachField.getDbFieldType() == FieldType.BASE64) {
								//stmt.setString(++cntrStmt, (String) eachField.getValueObj());
								byte[] strAsByte = eachField.getValueStr().getBytes();
								ByteArrayInputStream bais = new ByteArrayInputStream(strAsByte);
								stmt.setBinaryStream(++cntrStmt, bais, strAsByte.length);
							} else {
								throw new Hinderance("Unknown type for field: " + eachField.getDbFieldName().toUpperCase() + " when attempting to set it for SQL operation");
							}
						}
					} catch(Exception ex) {
						throw new Hinderance(ex, "Fail to place value into sql stmt, field: " + eachField.getDbFieldName().toUpperCase() + ", sql: " + stmt.toString());
					}
				}
			}
		}
	}

	public static String GetFromClause(String aMainTableName, Multimap<String, Record> aWhereBox) {
		String result = "";
		boolean needMainTableName = true;

		for(String eachTableName : aWhereBox.keySet()) {
			if (eachTableName.equalsIgnoreCase(aMainTableName)) needMainTableName = false;
			if (result.isEmpty() == false) result += ", ";
			result += eachTableName;
		}

		if (needMainTableName) {
			if (result.isEmpty() == false) result += ", ";
			result += aMainTableName;
		}
		return(result);
	}
	
	public static List<Field> GetWhereClause(Connection aConn, Multimap<String, Record> aWhereBox, StringBuffer aWhereStr) throws Exception {
		List<Field> fieldArr = new CopyOnWriteArrayList<>();
		aWhereBox.asMap().forEach((eachTableName, collection) -> {
			collection.forEach(eachWhereRec -> {
				try {
					List<Field> tmpFieldArr = GetWhereClause(aConn, eachTableName, eachWhereRec, aWhereStr);
					for (Field eachField: tmpFieldArr) {
						fieldArr.add(eachField);
					}
				} catch(Exception ex) {
					//App.logEror(Database.class, ex, "Fail to get where clause from multimap");
					//throw new Hinderance(ex, "Fail to get where clause from multimap");
					throw new RuntimeException(ex);
				} 
			});
		});
		return(fieldArr);
	}

	public static List<Field> GetWhereClause(Connection aConn, String aTableName, Record aWhereRec, StringBuffer aWhereStr) throws Exception {
		int cntrWhere = 0;
		String sqlWhere = "";
		List<Field> fieldArr = new CopyOnWriteArrayList<>();
		if (aWhereRec != null && aWhereRec.totalField() != 0) {
			for (Field eachField : aWhereRec.getFieldBox().values()) {
				if (cntrWhere != 0) {
					sqlWhere += " and ";
				}

				if (eachField instanceof FieldRecord) {
					String fobWhereStr = "";
					Record eachRecord = ((FieldRecord) eachField).getRecord();

					StringBuffer strBuffer = new StringBuffer();
					List<Field> fieldArrTmp = GetWhereClause(aConn, aTableName, eachRecord, strBuffer); // recursive
					for(Field eachFieldTmp : fieldArrTmp) {
						fieldArr.add(eachFieldTmp);
					}
					String sqlFobWhere = strBuffer.toString();
					if (sqlFobWhere.isEmpty() == false) {
						if (fobWhereStr.length() > 0) sqlFobWhere = " or " + sqlFobWhere;
						fobWhereStr += sqlFobWhere;
					}

					if (fobWhereStr.isEmpty() == false) {
						fobWhereStr = "(" + fobWhereStr + ")";
						sqlWhere += fobWhereStr; // and ((fob_tab.col1 = ? and fob_tab.col2 = ?) or (fob_tab.col1 = ? and fob_tab.col2 = ?))
					}
				} else {
					if (eachField.getFormulaStr().isEmpty()) {
						if (eachField.getValueObj(aConn) == null) {
							sqlWhere += aTableName + "." + eachField.getDbFieldName() + " is NULL";
						} else {
							sqlWhere += aTableName + "." + eachField.getDbFieldName() + " = ?";
						}
						fieldArr.add(eachField); // place in array for resultSet.setObject according to ? position
					} else {
						sqlWhere += eachField.getFormulaStr();
						if (eachField.getFormulaStr().contains("?")) {
							fieldArr.add(eachField); 
						}
					}
				}

				cntrWhere++;
			}
		}

		if (sqlWhere.isEmpty() == false) {
			if (sqlWhere.startsWith("(") == false) sqlWhere = "(" + sqlWhere + ")";
			if (aWhereStr.length() > 0) {
					sqlWhere = " and " + sqlWhere;
			}
			aWhereStr.append(sqlWhere);
		}
		
		return(fieldArr);
	}

	public static List<Field> GetFieldToUpdate(Connection aConn, String aTableName, Record aField2Update, StringBuffer aField2UpdateStr) throws Exception {
		String sqlField2Update = "";
		List<Field> fieldArr = new CopyOnWriteArrayList<>();
		if (aField2Update != null && aField2Update.totalField() != 0) {
			for (Field eachField : aField2Update.getFieldBox().values()) {
				if (eachField.isModified() == false) { // ignore fields that was never changed
					continue;
				}

				if (eachField.getDbFieldType() == FieldType.OBJECT) { // ignore and don't do anything to OBJECT field types
					continue;
				}
				if (eachField.getDbFieldType() == FieldType.OBJECTBOX) { // ignore and don't do anything to OBJECT field types
					continue;
				} 

				if (sqlField2Update.isEmpty() == false) { // only append comma in between columns
					sqlField2Update += ", ";
				} 
				if (eachField.getFormulaStr().isEmpty()) {
					if (Database.GetDbType(aConn) == DbType.MYSQL  || Database.GetDbType(aConn) == DbType.MARIADB || Database.GetDbType(aConn) == Database.DbType.ORACLE) {
						sqlField2Update += aTableName + "." + eachField.getDbFieldName() + " = ?";
					} else {
						sqlField2Update += eachField.getDbFieldName() + " = ?";
					}
				} else {
					if (Database.GetDbType(aConn) == DbType.MYSQL  || Database.GetDbType(aConn) == DbType.MARIADB || Database.GetDbType(aConn) == Database.DbType.ORACLE) {
						sqlField2Update += aTableName + "." + eachField.getDbFieldName() + " = " + eachField.getFormulaStr();
					} else {
						sqlField2Update += eachField.getDbFieldName() + " = " + eachField.getFormulaStr();
					}
				}
				fieldArr.add(eachField); // place in array for resultSet.setObject according to ? position
				//App.logDebg("Field to update: " + eachField.getFieldName() + ", value: " + eachField.getValueStr());
			}
		}
		aField2UpdateStr.append(sqlField2Update);
		return(fieldArr);
	}

	public static List<Field> GetFieldToInsert(String aTableName, Record aRec2Insert, StringBuffer aField, StringBuffer aHolder) throws Exception {
		String strField = "";
		String strHolder = "";
		List<Field> fieldArr = new CopyOnWriteArrayList<>();
		if (aRec2Insert != null && aRec2Insert.totalField() != 0) {
			for (Field eachField : aRec2Insert.getFieldBox().values()) {

				if (eachField.getDbFieldType() == FieldType.OBJECT) {
					continue;
				} // ignore and don't do anything to OBJECT field types
				if (eachField.getDbFieldType() == FieldType.OBJECTBOX) {
					continue;
				} // ignore and don't do anything to OBJECT field types

				if (strField.isEmpty() == false) {
					strField += ", ";
				} // only append comma in between columns
				if (strHolder.isEmpty() == false) {
					strHolder += ", ";
				} // only append comma in between columns

				//strField += aTableName + "." + eachField.getFieldName();
				strField += eachField.getDbFieldName();
				if (eachField.getFormulaStr().isEmpty()) {
					strHolder += "?";
				} else {
					strHolder += eachField.getFormulaStr();
				}
				fieldArr.add(eachField); // place in array for resultSet.setObject according to ? position
				//App.logDebg("Field to Insert: " + eachField.getFieldName() + ", value: " + eachField.getValueStr());
			}
		}
		aField.append(strField);
		aHolder.append(strHolder);
		return(fieldArr);
	}

	public static String DdlForDecrypt(String aFieldName) {
		String result = aFieldName;
		return(result);
	}
	
	public int fetch(Table aTable, Record aSelect, Record aWhere) throws Exception {
		Connection conn = this.connPool.getConnection();
		try {
			return(aTable.fetch(conn, aSelect, aWhere));
		} finally {
			if (conn != null) {
				this.connPool.freeConnection(conn);
			}
		}		
	}
	
	public void alterTableAddColumn(Table aTable) throws Exception {
		Connection conn = this.connPool.getConnection();
		try {
			AlterTableAddColumn(conn, aTable);
		} finally {
			if (conn != null) {
				this.connPool.freeConnection(conn);
			}
		}		
	}

	public static void AlterTableRenameIndex(Connection aConn, String aTableName, String aOldIndexName, String aNewIndexName) throws Exception {
		String sqlRename = "";
		if (GetDbType(aConn) == DbType.MYSQL || GetDbType(aConn) == DbType.MARIADB) {
			// MySQL prior to 5.7 need to drop and re-create, kiv
			sqlRename = "alter table " + aTableName + " rename index " + aOldIndexName + " to " + aNewIndexName;
		} else if (GetDbType(aConn) == DbType.POSTGRESQL) {
			sqlRename = "alter index " + aOldIndexName + " rename to " + aNewIndexName;
		} else if (GetDbType(aConn) == DbType.ORACLE) {
			throw new Hinderance("Not supported SQL for db type: " + GetDbType(aConn));
		} else {
			throw new Hinderance("Not supported SQL for db type: " + GetDbType(aConn));
		}

		ExecuteDdl(aConn, sqlRename);
	}

	public static void AlterIndexRenamePk(Connection aConn, String aOldTableName, String aNewTableName) throws Exception {
		String sqlRename = "";
		boolean doExecute = true;
		if (GetDbType(aConn) == DbType.MYSQL || GetDbType(aConn) == DbType.MARIADB) {
			doExecute = false;
			App.logInfo("MYSQL primary key is system generated, will not do any renaming");
		} else if (GetDbType(aConn) == DbType.POSTGRESQL) {
			String oldPkName = aOldTableName + "_pkey";
			String newPkName = aNewTableName + "_pkey";
			sqlRename = "alter index " + oldPkName + " rename to " + newPkName;
		} else if (GetDbType(aConn) == DbType.ORACLE) {
			throw new Hinderance("Not supported SQL for db type: " + GetDbType(aConn));
		} else {
			throw new Hinderance("Not supported SQL for db type: " + GetDbType(aConn));
		}
		try {
			if (doExecute) ExecuteDdl(aConn, sqlRename);
		} catch(Exception ex) {
			App.logEror(ex, "Error, will not be renaming the primary key constraint from: " + aOldTableName + ", to: " + aNewTableName);
		}
	}

	public static void AlterTableRenameField(Connection aConn, Table aTable, String aOldColName, String aNewColName) throws Exception {
		String sqlRename = "";
		if (GetDbType(aConn) == DbType.MYSQL) {
			String dataType = aTable.getColumnDataType(aConn, aOldColName); // check this, ver prior to 5.7 can't get the column type
			sqlRename = "alter table " + aTable.tableName + " change " + aOldColName + " " + aNewColName + " " + dataType;
		} else if (GetDbType(aConn) == DbType.POSTGRESQL) {
			sqlRename = "alter table " + aTable.tableName + " rename column " + aOldColName + " to " + aNewColName;
		} else if (GetDbType(aConn) == DbType.ORACLE) {
			throw new Hinderance("Not supported SQL for db type: " + GetDbType(aConn));
		} else {
			throw new Hinderance("Not supported SQL for db type: " + GetDbType(aConn));
		}
		ExecuteDdl(aConn, sqlRename);
	}

	public static void AlterTableRenameTable(Connection aConn, Table aTable, String aNewName) throws Exception {
		String sqlRename = "";
		if (GetDbType(aConn) == DbType.MYSQL) {
			sqlRename = "rename table " + aTable.tableName + " to " + aNewName;
		} else if (GetDbType(aConn) == DbType.POSTGRESQL) {
			sqlRename = "alter table " + aTable.tableName + " rename to " + aNewName;
		} else if (GetDbType(aConn) == DbType.ORACLE) {
			sqlRename = "rename table " + aTable.tableName + " to " + aNewName;
		} else {
			throw new Hinderance("Not supported SQL for db type: " + GetDbType(aConn));
		}
		ExecuteDdl(aConn, sqlRename);
	}

	public static void AlterTableAddColumn(Connection aConn, Table aTable) throws Exception {
		String strSql = "alter table " + aTable.getTableName() + " add ";
		int totalField = 0;
		for (Field eachField : aTable.getMetaRec().getFieldBox().values()) {
			if (eachField.getDbFieldType() == FieldType.OBJECT) {
				continue;
			} // ignore and don't do anything to OBJECT field types
			if (eachField.getDbFieldType() == FieldType.OBJECTBOX) {
				continue;
			} // ignore and don't do anything to OBJECTBOX field types

			if (totalField == 0) {  
				if (Database.GetDbType(aConn) == DbType.MYSQL || Database.GetDbType(aConn) == DbType.ORACLE) {
					strSql += "(";
				} else {
					// do nothing
				}
			} else {
				if (Database.GetDbType(aConn) == DbType.MYSQL || Database.GetDbType(aConn) == DbType.ORACLE) {
					strSql += ", ";
				} else {
					strSql += ", add ";
				}
			}
			strSql += eachField.getDbFieldName(); 
			strSql += " " + DdlFieldType(aConn, eachField.getDbFieldType(), eachField.getFieldSize());
			totalField++;
		}
		if (totalField != 0) {
			if (Database.GetDbType(aConn) == DbType.MYSQL || Database.GetDbType(aConn) == DbType.ORACLE) {
				strSql += ")";
			} else {
				// do nothing
			}
		}
		ExecuteDdl(aConn, strSql);
	}

	public boolean anyFieldExist(String aName, Record aRecord) throws Exception {
		Connection conn = this.connPool.getConnection();
		try {
			return(AnyFieldExist(conn, aName, aRecord));
		} finally {
			if (conn != null) {
				this.connPool.freeConnection(conn);
			}
		}		
	}

	public static boolean AnyFieldExist(Connection conn, String aName, Record aRecord) throws Exception {
		boolean result = false;
		Statement stmt = null;
		ResultSet rset = null;
		try {
			stmt = conn.createStatement();
			String sql = "select * from " + aName + " where 1 = 2";
			rset = stmt.executeQuery(sql);
			ResultSetMetaData metaData = rset.getMetaData();
			for(int cntrCol = 0; cntrCol < metaData.getColumnCount(); cntrCol++) {
				String fieldName = metaData.getColumnName(cntrCol + 1).toLowerCase().trim();
				for(Field eachField : aRecord.getFieldBox().values()) {
					if (eachField.getDbFieldName().toLowerCase().trim().equals(fieldName)) {
						result = true;
						break;
					}
				}
			}
		} finally {
			if (rset != null) {
				rset.close();
			}
			if (stmt!= null) {
				stmt.close();
			}
		}
		return(result);
	}

	public static List<List<String>> ExecuteSQL(Connection aConn, String aSqlStr, List<Field> aBindField) throws Exception {
		PreparedStatement stmt = aConn.prepareStatement(aSqlStr);
		return(ExecuteSQL(aConn, stmt, aBindField));
	}

	public static List<List<String>> ExecuteSQL(Connection aConn, PreparedStatement aStmt, List<Field> aBindField) throws Exception {
		try {
			List<List<String>> listOfLists = new CopyOnWriteArrayList<>();
			Database.SetStmtValue(aConn, aStmt, aBindField);
			ResultSet rset = aStmt.executeQuery();
			ResultSetMetaData metaData = rset.getMetaData();
			int columns = metaData.getColumnCount();
			while (rset.next()) {
				List<String> list = new CopyOnWriteArrayList<>();
				for (int cntr = 0; cntr < columns; cntr++) {
					list.add(rset.getString(cntr + 1));
				}
				listOfLists.add(list);
			}
			return listOfLists;
		} catch(Exception ex) {
			throw new Hinderance(ex, "Fail sql: " + aStmt.toString());
		}
	}

	public static boolean DuplicateError(Exception ex) {
		boolean result = false;
		String strStack;
		if (ex instanceof Hinderance) {
			strStack = ((Hinderance) ex).getExMsg();
		} else {
			strStack = ExceptionUtils.getStackTrace(ex);
		}
		if (strStack.contains("Duplicate entry")) {
			result = true;
		}
		return(result);
	}

	public static String GetYearSql(Connection aConn, String aTableName, String aFieldName) throws Exception {
		String result = "";
		if (GetDbType(aConn) == DbType.MYSQL) {
			result = "year(" + aTableName + "." + aFieldName + ")";
		} else if (GetDbType(aConn) == DbType.POSTGRESQL) {
			result = "to_char(" + aTableName + "." + aFieldName + ", 'YYYY')::integer";
		} else if (GetDbType(aConn) == DbType.ORACLE) {
		} else {
		}
		return(result);
	}

	public static String GetMonthSql(Connection aConn, String aTableName, String aFieldName) throws Exception {
		String result = "";
		if (GetDbType(aConn) == DbType.MYSQL) {
			result = "month(" + aTableName + "." + aFieldName + ")";
		} else if (GetDbType(aConn) == DbType.POSTGRESQL) {
			result = "to_char(" + aTableName + "." + aFieldName + ", 'MM')::integer";
		} else if (GetDbType(aConn) == DbType.ORACLE) {
		} else {
		}
		return(result);
	}

	public static String GetDaySql(Connection aConn, String aTableName, String aFieldName) throws Exception {
		String result = "";
		if (GetDbType(aConn) == DbType.MYSQL) {
			result = "day(" + aTableName + "." + aFieldName + ")";
		} else if (GetDbType(aConn) == DbType.POSTGRESQL) {
			result = "to_char(" + aTableName + "." + aFieldName + ", 'DD')::integer";
		} else if (GetDbType(aConn) == DbType.ORACLE) {
		} else {
		}
		return(result);
	}

	public static void AddColumn(Connection aConn, Table aTargetTable) throws Exception {
		String targetTableName = aTargetTable.getTableName().toLowerCase();
		List<Field> missingField = new CopyOnWriteArrayList<>();
		Table existingTable = new Table(targetTableName);
		existingTable.initMeta(aConn);
		if (Database.TableExist(aConn, targetTableName)) {
			for (Field eachField : aTargetTable.getMetaRec().getFieldBox().values()) {
				if (existingTable.fieldExist(eachField.getDbFieldName()) == false) {
					missingField.add(eachField);
				}
			}
		} else {
			throw new Hinderance("Cannot add column on a non existing table: " + aTargetTable.getTableName());
		}

		String strSqlFull = "alter table " +  targetTableName;
		String strSqlCol = "";
		for (Field eachField : missingField) {
			String dbFieldType = DdlFieldType(aConn, eachField.getDbFieldType(), eachField.getFieldSize());
			if (strSqlCol.isEmpty() == false) strSqlCol += ", ";
			strSqlCol += " add column " + eachField.getDbFieldName() + " " + dbFieldType;
		}

		if (strSqlCol.isEmpty() == false) {
			strSqlFull += " " + strSqlCol;
			ExecuteDdl(aConn, strSqlFull);
		} else {
			// no column to add, do nothing
		}
	}

	public static String RightPadSql(Connection aConn, String aColName, int aLen, String aPadStr) throws Exception {
		String result = "";
		if (GetDbType(aConn) == DbType.MYSQL || GetDbType(aConn) == DbType.POSTGRESQL) {
			result = "rpad (" + aColName + ", " + String.valueOf(aLen) + ", '" + aPadStr + "')";
		} else if (GetDbType(aConn) == DbType.ORACLE) {
			throw new Hinderance("Oraclel RightPadSql is not supprted yeat");
		} else {
		}
		return(result);
	}

	public static String LeftPadSql(Connection aConn, String aColName, int aLen, String aPadStr) throws Exception {
		String result = "";
		if (GetDbType(aConn) == DbType.MYSQL || GetDbType(aConn) == DbType.POSTGRESQL) {
			result = "lpad (" + aColName + ", " + String.valueOf(aLen) + ", '" + aPadStr + "')";
		} else if (GetDbType(aConn) == DbType.ORACLE) {
			throw new Hinderance("Oraclel LeftPadSql is not supprted yeat");
		} else {
		}
		return(result);
	}

	public static String DateTimeForSort(Connection aConn, String aColName) throws Exception {
		String result = "";
		if (GetDbType(aConn) == DbType.POSTGRESQL) {
			result = "to_char(" + aColName + ", 'yyyyMMddHHmmss')";
		} else if (GetDbType(aConn) == DbType.MYSQL) {
			result = "date_format(" + aColName + ", '%Y%m%d%H%i%S')";
		} else if (GetDbType(aConn) == DbType.ORACLE) {
			throw new Hinderance("Oraclel DateForSort is not supprted yeat");
		} else {
		}
		return(result);
	}

	public static String DateForSort(Connection aConn, String aColName) throws Exception {
		String result = "";
		if (GetDbType(aConn) == DbType.POSTGRESQL) {
			result = "to_char(" + aColName + ", 'yyyyMMdd')";
		} else if (GetDbType(aConn) == DbType.MYSQL) {
			result = "date_format(" + aColName + ", '%Y%m%d')";
		} else if (GetDbType(aConn) == DbType.ORACLE) {
			throw new Hinderance("Oraclel DateForSort is not supprted yeat");
		} else {
		}
		return(result);
	}

	public static String Num2StrSql(Connection aConn, String aColName) throws Exception {
		String result = "";
		if (GetDbType(aConn) == DbType.POSTGRESQL) {
			/*
			String numFormat = "";
			for(int cntr = 0; cntr < Generic.MAX_DIGIT_FOR_SORT; cntr++) numFormat += "9";
			result = "to_char(" + aColName + ", '" + numFormat + "')";
			*/
			result = "cast(" + aColName + " as varchar)";
		} else if (GetDbType(aConn) == DbType.MYSQL) {
			result = "convert(" + aColName + ", CHAR)";
		} else if (GetDbType(aConn) == DbType.ORACLE) {
			throw new Hinderance("Oraclel DateForSort is not supprted yeat");
		} else {
		}
		return(result);
	}

	public static String Float2StrSql(Connection aConn, String aColName) throws Exception {
		String result = "";
		if (GetDbType(aConn) == DbType.POSTGRESQL) {
			String floatFormat = "";
			for(int cntr = 0; cntr < Generic.MAX_FLOAT_FOR_SORT; cntr++) {
				if (cntr == 16) // must match Generic.FloatFormatForSort
					floatFormat += "D";
				else
					floatFormat += "9";
			}
			result = "to_char(" + aColName + ", '" + floatFormat + "')";
		} else if (GetDbType(aConn) == DbType.MYSQL) {
			result = "convert(" + aColName + ", DECIMAL(" + Generic.MAX_FLOAT_FOR_SORT + ", 3))"; // 3 must match Generic.FloatFormatForSort
		} else if (GetDbType(aConn) == DbType.ORACLE) {
			throw new Hinderance("Oraclel DateForSort is not supprted yeat");
		} else {
		}
		return(result);
	}
}
