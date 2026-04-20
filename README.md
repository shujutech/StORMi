## StORMi

<img src="http://shujutech.mywire.org/img/dblogo5.png" alt="alt text" width="50%" height="50%">

StORMi maps Java POJO into relational databases. StORMi is the only true ORM that is able to map all OO concept into a relational database.


## Supported Database

- MySQL
- MariaDB
- Postgres
- Oracle


## Features

StORMi supports mapping the following Java OOP concepts:

- Class (maps into a table)
- Properties (maps class properties into database fields for all primitive datatypes)
- Class Properties (maps complex datatypes of type class, in OOP also known as a member of)
- Inheritance (maps the inheritance relationship of a class with the inherited class)
- Abstract Class (places the properties of an abstract class into a concrete class)
- Polymorphism (enable a member of class type to behave polymorphically)
- Array of Objects (supports handling of array of objects)

StORMi supports the following database operations:

- DDL Creation (generate DDL to create tables, columns for the defined class in Java)
- Persistence (when a Java POJO class is persisted, StORMi will handle all the underlying intricancies)
- Deletion (when deleting a class, StORMi can also delete its related class when configured to do so)
- Updating (like Persistence and Deletion, many complex class relationship is being manage by StORMi)
- No SQL hence can be easily ported to any SQL database, DDL/DML can be done through java methods

## Benefits

- Allow full enterprise team to use standard OOP as the universal design model
- Never redesign your data model again with StORMi standard, consistent and reusable model
- Allows team to have common understanding of a single design principal and concept
- No duplication of enterprise information
- Unlimited standardise scaling capabilities for all your enterprise information system

## Examples

#### Defining database object 

````java
// anything that extends Clasz will be map into the database
public class Addr extends Clasz {
	@ReflectField(type=FieldType.STRING, size=32, displayPosition=5) 
	public static String Addr1;
  
	@ReflectField(type=FieldType.STRING, size=32, displayPosition=10) 
	public static String Addr2;
  
	@ReflectField(type=FieldType.STRING, size=32, displayPosition=15) 
	public static String Addr3;
  
	@ReflectField(type=FieldType.STRING, size=8, displayPosition=20) 
	public static String PostalCode;
  
	@ReflectField(type=FieldType.OBJECT, deleteAsMember=false, 
	clasz=biz.shujutech.bznes.Country.class, displayPosition=35, prefetch=true, lookup=true) 
	public static String Country; 
  
	@ReflectField(type=FieldType.OBJECT, deleteAsMember=false, 
	clasz=biz.shujutech.bznes.State.class, displayPosition=40, prefetch=true, lookup=true) 
	public static String State; 
  
	@ReflectField(type=FieldType.OBJECT, deleteAsMember=false, 
	clasz=biz.shujutech.bznes.City.class, displayPosition=45, prefetch=true, lookup=true) 
	public static String City; 
}
````

#### Persisting objects (insert or update)

````java
	Person employee = (Person) ObjectBase.CreateObject(conn, Person.class);
	employee.setName("Ken Miria");
	employee.setBirthDate(new DateTime());
	employee.setGender(Gender.Male);
	employee.setNationality(Country.UnitedStates);
	employee.setMaritalStatus(Marital.Married);
	company.addEmployee(conn, employee);

	ObjectBase.PersistCommit(conn, company);
````

#### Deleting objects

````java
	// create the object to delete and set a unique search criteria
	Person person = (Person) objectDb.createObject(Person.class); 
	person.setName("Edward Yourdon");
	if (person.populate(conn) == true) {
		if (person.deleteCommit(conn)) {
			App.logInfo("Deleted person Edward Yourdon");
		} else {
			throw new Hinderance("Fail to delete person Edward Yourdon");
		}
	}
````
				
## Quick Start

1. Install Postgres and create a database name 'stormi' using 'postgres' user and 'abc1234' password.

2. Install Java and Maven if you do not have them.

3. Download StORMi soruce code from github and extract them to your install directory.

4. Build StORMi by going into the installed directory and run

````bash
	mvn install
````

5. Go into the example directory and run 

````bash
	mvn clean compile assembly:single
````

6. In the example/target directory, copy stormi.proprties into the target directory

````bash
	cp ../stormi.properties .
````

7. Run the example application with

````bash
	java -jar example-1.0-SNAPSHOT-jar-with-dependencies.jar
````

8. You will get the follwoing output

````bash
	18Jun2021 22:56:14 INFO Log level is at: DEBG
	18Jun2021 22:56:14 INFO Working directory: C:\Shujutech\StORMi-main\example\target
	18Jun2021 22:56:14 INFO Found property file at: C:\Shujutech\StORMi-main\example\target\stormi.properties
	18Jun2021 22:56:14 INFO Configuration from: C:\Shujutech\StORMi-main\example\target\stormi.properties
	18Jun2021 22:56:14 INFO Log file: C:\Users\Admin\AppData\Local\Temp\\App.202106.log
	18Jun2021 22:56:14 INFO Log on console: true
	18Jun2021 22:56:14 INFO Log next switch at: 2021-07-01T00:00:00.244+08:00
	18Jun2021 22:56:14 INFO Maximum thread: 16
	18Jun2021 22:56:14 COFG OS name: Windows 10
	18Jun2021 22:56:14 COFG OS architecture: amd64
	18Jun2021 22:56:14 COFG OS version: 10.0
	18Jun2021 22:56:14 COFG Java classpath: example-1.0-SNAPSHOT-jar-with-dependencies.jar
	18Jun2021 22:56:14 INFO Total db connection available for threading: 2
	18Jun2021 22:56:14 COFG Jdbc, connecting with url = jdbc:postgresql://localhost:5432/stormi
	18Jun2021 22:56:14 COFG Jdbc, connecting with user = postgres
	18Jun2021 22:56:14 COFG Jdbc, connecting with password = *********
	18Jun2021 22:56:14 COFG Jdbc, connecting with init conn = 2
	18Jun2021 22:56:14 COFG Jdbc, connecting with max conn = 32
	18Jun2021 22:56:14 COFG Jdbc, connecting with time out= 30
	18Jun2021 22:56:15 WARN [ConnectionPool] Getting jdbc connection, available free connection to get from: 2
	18Jun2021 22:56:15 INFO [Clasz] Creating table for class: 'LeaveForm'
	18Jun2021 22:56:15 INFO [Simple] Successfully save leave form into the database!
	18Jun2021 22:56:15 DEBG [ConnectionPool] Released jdbc connection, total free connection: 2
````

9. Check your database for the created tables


## Usage

To use StORMi without maven, copy the jar file in the 'relase' directory into your java project library. 

If you're using maven, dowload the release directory and run the following maven command:

````bash
	mvn install:install-file -Dfile=./StORMi-1.0-SNAPSHOT.jar -DpomFile=./pom.xml
````

After installing StORMi into your maven repository, use the following pom dependency in you maven project:

````maven
	<dependency>
		<groupId>biz.shujutech</groupId>
		<artifactId>StORMi</artifactId>
		<version>1.0-SNAPSHOT</version>
	</dependency>
````

To try out StORMi, go to the 'example' directory and compile 'Simple.java' and execute it. To compile with IDE (e.g. eclipse or netbeans) you can import the maven project.

Before executing the 'example', create a database (either postgresql or mysql) and configure it's jdbc properties in the file 'stormi.properties' (jdbcUser, jdbcPassword, jdbcUrl). The default configured is (schema: stormi, login: postgres, password: abc1234)


## OO Implementations

### Inheritance

StORMi automatically maps Java class inheritance into relational database tables. When a class extends another class (which ultimately extends `Clasz`), StORMi creates separate tables for each class in the hierarchy and links them using intermediary **inheritance tables** prefixed with `ih_`.

### How It Works

StORMi uses these naming conventions for database artifacts:

| Java Concept | DB Prefix | Example |
|---|---|---|
| Class table | `cz_` | `cz_person` |
| Inheritance link table | `ih_` | `ih_employee` |
| Primary key column | `cz_<class>_pk` | `cz_person_pk` |

When `CreateObject` is called, StORMi traverses the Java class hierarchy. For each non-abstract parent class (up until `Clasz`), it:

1. Creates a table for the class (`cz_` prefix)
2. Creates an inheritance link table (`ih_` prefix) containing the PKs of both child and parent

For a 3-level hierarchy `User -> Employee -> Person`, the generated schema looks like this:

````
   user     employee   person
     \       /  \       /   
      \     /    \     /   
     ih_user    ih_employee
````

The SQL to retrieve all the related records:

````sql
SELECT * FROM cz_user, ih_user, cz_employee, ih_employee, cz_person
WHERE cz_user.cz_user_pk = ih_user.cz_user_pk
AND ih_user.cz_employee_pk = cz_employee.cz_employee_pk
AND cz_employee.cz_employee_pk = ih_employee.cz_employee_pk
AND ih_employee.cz_person_pk = cz_person.cz_person_pk;
````

### Step 1: Define the Parent Class

Every persistable class must ultimately extend `Clasz`. Define your base/parent class with its fields using the `@ReflectField` annotation:

````java
// Person class -> maps to table "cz_person"
public class Person extends Clasz {

    @ReflectField(type = FieldType.STRING, size = 64, displayPosition = 10)
    public static String Name;

    @ReflectField(type = FieldType.DATETIME, displayPosition = 20)
    public static String BirthDate;

    public String getName() throws Exception {
        return this.getValueStr(Name);
    }

    public void setName(String aName) throws Exception {
        this.setValueStr(Name, aName);
    }
}
````

### Step 2: Define the Child Class

To create an inheritance relationship, simply extend the parent class using standard Java `extends`:

````java
// Employee class -> maps to table "cz_employee"
// Inherits from Person, linked via "ih_employee" table
public class Employee extends Person {

    @ReflectField(type = FieldType.STRING, size = 32, displayPosition = 30)
    public static String Department;

    @ReflectField(type = FieldType.STRING, size = 16, displayPosition = 40)
    public static String EmployeeId;

    public String getDepartment() throws Exception {
        return this.getValueStr(Department);
    }

    public void setDepartment(String aDept) throws Exception {
        this.setValueStr(Department, aDept);
    }

    public String getEmployeeId() throws Exception {
        return this.getValueStr(EmployeeId);
    }

    public void setEmployeeId(String aId) throws Exception {
        this.setValueStr(EmployeeId, aId);
    }
}
````

### Step 3: Add More Levels (Optional)

````java
// User class -> maps to table "cz_user"
// Inherits from Employee, linked via "ih_user" table
public class User extends Employee {

    @ReflectField(type = FieldType.STRING, size = 32, displayPosition = 50)
    public static String LoginId;

    @ReflectField(type = FieldType.STRING, size = 64, displayPosition = 60)
    public static String Password;

    public String getLoginId() throws Exception {
        return this.getValueStr(LoginId);
    }

    public void setLoginId(String aLoginId) throws Exception {
        this.setValueStr(LoginId, aLoginId);
    }
}
````

### Step 4: Create, Populate, and Persist

Use `ObjectBase.CreateObject()` to instantiate the object. StORMi will automatically create all necessary tables (`cz_user`, `cz_employee`, `cz_person`, `ih_user`, `ih_employee`) via DDL if they don't exist.

````java
ObjectBase objectDb = new ObjectBase();
String[] args = { "stormi.properties" };
objectDb.setupApp(args);
objectDb.setupDb();
Connection conn = objectDb.getConnPool().getConnection();

// Create a User object (automatically sets up the full inheritance chain)
User user = (User) ObjectBase.CreateObject(conn, User.class);

// Set fields from User
user.setLoginId("jdoe");

// Set fields from Employee (inherited)
user.setDepartment("Engineering");
user.setEmployeeId("EMP001");

// Set fields from Person (inherited)
user.setName("John Doe");

// Persist - StORMi inserts into cz_user, cz_employee, cz_person,
// and links them via ih_user and ih_employee automatically
user.persistCommit(conn);
````

### Step 5: Fetching and Accessing Parent Fields

When you fetch a `User` object, StORMi automatically traverses up the inheritance tree, joining through the `ih_` tables to populate all parent fields. You can also navigate to a specific parent object using `GetInheritanceObject`:

````java
User user = (User) ObjectBase.CreateObject(conn, User.class);
user.setLoginId("jdoe");
if (user.populate(conn)) {
    // Access parent-level fields directly
    String name = user.getName();           // from Person
    String dept = user.getDepartment();     // from Employee

    // Or get the specific parent object
    Person personObj = (Person) Clasz.GetInheritanceObject(user, Person.class);
}
````

### Step 6: Deleting

Deletion also traverses the inheritance tree. StORMi tracks a child count on each parent record. A parent record is only deleted when its child count reaches zero.

````java
User user = (User) ObjectBase.CreateObject(conn, User.class);
user.setLoginId("jdoe");
if (user.populate(conn)) {
    user.deleteCommit(conn);
    // Deletes cz_user row, ih_user row, cz_employee row (if no other children),
    // ih_employee row, and cz_person row (if no other children)
}
````

### Key Rules

1. All persistable classes must extend `Clasz` (directly or indirectly).
2. Fields must be `public static String` annotated with `@ReflectField`.
3. No SQL is needed -- StORMi generates all DDL and DML automatically.
4. Abstract classes are handled differently: their fields are merged into the nearest concrete child class table rather than getting their own table.
5. Each concrete class in the hierarchy gets its own `cz_` table and an `ih_` link table connecting it to its parent.

### Object Properties

StORMi maps Java class properties to relational database structures using the `@ReflectField` annotation. Properties fall into two categories: **primitive types** (mapped directly to database columns) and **object types** (mapped via relationship tables or inline flattening).

#### Primitive Type Properties

Primitive properties use one of the built-in `FieldType` values: `STRING`, `INTEGER`, `LONG`, `FLOAT`, `BOOLEAN`, `DATE`, `DATETIME`, `ENCRYPT`, `HTML`, or `BASE64`. Each primitive type maps directly to a column in the class's database table.

**Example:**

````java
public class Addr extends Clasz {
    @ReflectField(type=FieldType.STRING, size=32, displayPosition=5)
    public static String Addr1;

    @ReflectField(type=FieldType.STRING, size=8, displayPosition=20)
    public static String PostalCode;
}
````

During class creation, `CreateFieldFromClass` in `Clasz.java` reads each `@ReflectField` annotation. For primitive types, it calls `createField(dbFieldName, fieldType)` (or the overload with size), which adds a column to the class's database table.

#### Object Type Properties

Object-type properties use `FieldType.OBJECT` (single reference) or `FieldType.OBJECTBOX` (collection). They require the `clasz` attribute to specify the target class.

##### Single Object Reference (`FieldType.OBJECT`)

Represents a "has-a" (member-of) relationship to another `Clasz`. Internally backed by `FieldObject<Ty>`.

**Example:**

````java
@ReflectField(type=FieldType.OBJECT, deleteAsMember=false,
    clasz=biz.shujutech.bznes.Country.class, displayPosition=35,
    prefetch=true, lookup=true)
public static String Country;
````

**Database mapping:** StORMi creates an `iv_[classname]` (instant variable) table that stores the parent's primary key and the child object's `object_id`. If the field is polymorphic, an additional `leaf_class` column stores the concrete class name.

**Key annotation attributes for OBJECT fields:**

| Attribute | Default | Description |
|:---|:---|:---|
| `clasz` | `Clasz.class` | The target class of the member object |
| `inline` | `false` | If `true`, flatten the child's fields into the parent table |
| `prefetch` | `false` | If `true`, eagerly load the member when the parent is populated |
| `deleteAsMember` | `false` | If `true`, deleting the parent cascades to this member |
| `polymorphic` | `false` | If `true`, store the concrete leaf class name for polymorphic resolution |
| `lookup` | `false` | Marks the field as a lookup/reference data field |

**Inline Object Fields:** When `inline=true`, the child object's fields are flattened directly into the parent's table (prefixed with the field name). No separate relationship table is created.

**Lazy Fetching:** When `prefetch=false` (the default), the member object is loaded on-demand. `FieldObject.getValueObj()` checks the `FetchStatus` and triggers a database fetch only when the value is first accessed.

**Polymorphic Fields:** If the declared type is abstract or `polymorphic=true`, StORMi stores the actual concrete class name in a `leaf_class` column. At fetch time, `ObjectBase.GetEffectiveClass` resolves the real type before instantiation.

##### Collection of Objects (`FieldType.OBJECTBOX`)

Represents a one-to-many relationship. Internally backed by `FieldObjectBox<Ty>`, which stores members in a `ConcurrentHashMap`.

**Database mapping:** StORMi creates an `iw_[classname]_[fieldname]` table with three columns: the parent PK, the child `object_id`, and a `leaf_class` column for polymorphic support.

#### Persistence Flow

When `ObjectBase.PersistCommit()` is called:

1. **Primitive fields** are inserted/updated directly as columns in the object's table.
2. **OBJECT fields** -- the member object is persisted first (recursively), then the `iv_` relationship table is updated. Inline objects are flattened before the parent's insert.
3. **OBJECTBOX fields** -- each member in the collection is persisted, then the `iw_` link table is updated.

## Contact Us

For any further support, please contact me at shujutech@gmail.com



