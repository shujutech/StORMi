## StORMi

<img src="http://shujutech.mywire.org/img/dblogo5.png" alt="alt text" width="50%" height="50%">

StORMi maps Java POJO into relational databases. StORMi is the only true ORM that is able to map all OO concept into a relational database.

<br>

## Supported Database

- MySQL
- MariaDB
- Postgres
- Oracle

<br>

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

<br>

## Benefits

- Allow full enterprise team to use standard OOP as the universal design model
- Never redesign your data model again with StORMi standard, consistent and reusable model
- Allows team to have common understanding of a single design principal and concept
- No duplication of enterprise information
- Unlimited standardise scaling capabilities for all your enterprise information system

<br>

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

<br>

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

<br>

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

<br>

## OO Implementations


### 1. **Object Properties**

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

<br>

### 2. **Inheritance**

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

#### Step 1: Define the Parent Class

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

#### Step 2: Define the Child Class

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

#### Step 3: Add More Levels (Optional)

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

#### Step 4: Create, Populate, and Persist

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

#### Step 5: Fetching and Accessing Parent Fields

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

#### Step 6: Deleting

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

#### Key Rules

1. All persistable classes must extend `Clasz` (directly or indirectly).
2. Fields must be `public static String` annotated with `@ReflectField`.
3. No SQL is needed -- StORMi generates all DDL and DML automatically.
4. Abstract classes are handled differently: their fields are merged into the nearest concrete child class table rather than getting their own table.
5. Each concrete class in the hierarchy gets its own `cz_` table and an `ih_` link table connecting it to its parent.

<br>

### 3. **Object Composition**

Object Composition is the OOP "has-a" relationship where one class contains another class as a member. StORMi maps this into the relational database using **instant variable tables** (`iv_` prefix) for single member objects and **instant variable with array** tables (`iw_` prefix) for collections of member objects.

#### How It Works

When a class declares a field with `FieldType.OBJECT`, StORMi creates an `iv_[classname]` table to store the relationship between the parent object's PK and the member object's `object_id`. Each `OBJECT` field becomes a column in this table. For `FieldType.OBJECTBOX`, a separate `iw_[classname]_[fieldname]` table is created per collection field.

| Java Concept | DB Prefix | Example |
|---|---|---|
| Class table | `cz_` | `cz_addr` |
| Single member link table | `iv_` | `iv_addr` |
| Collection member link table | `iw_` | `iw_company_employee` |

For a `Company` class that has a single `Addr` member and a collection of `Employee` members:

````
   cz_company          cz_addr         cz_employee
       |                  |                 |
   iv_company ------> cz_addr_pk    iw_company_employee --> cz_employee_pk
   (addr column)                    (employee column)
````

#### Step 1: Define a Class with Composed Members

Use `@ReflectField(type=FieldType.OBJECT)` for a single member-of relationship. Specify the member's class with the `clasz` attribute:

````java
public class Addr extends Clasz {
    @ReflectField(type=FieldType.STRING, size=32, displayPosition=5)
    public static String Addr1;

    @ReflectField(type=FieldType.STRING, size=32, displayPosition=10)
    public static String Addr2;

    @ReflectField(type=FieldType.STRING, size=8, displayPosition=20)
    public static String PostalCode;

    // Composition: Addr "has-a" Country
    @ReflectField(type=FieldType.OBJECT, deleteAsMember=false,
        clasz=biz.shujutech.bznes.Country.class, displayPosition=35,
        prefetch=true, lookup=true)
    public static String Country;

    // Composition: Addr "has-a" State
    @ReflectField(type=FieldType.OBJECT, deleteAsMember=false,
        clasz=biz.shujutech.bznes.State.class, displayPosition=40,
        prefetch=true, lookup=true)
    public static String State;

    // Composition: Addr "has-a" City
    @ReflectField(type=FieldType.OBJECT, deleteAsMember=false,
        clasz=biz.shujutech.bznes.City.class, displayPosition=45,
        prefetch=true, lookup=true)
    public static String City;
}
````

StORMi will automatically create:
- `cz_addr` -- the class table with columns for `Addr1`, `Addr2`, `PostalCode`
- `iv_addr` -- the composition link table with columns `cz_addr_pk`, `country`, `state`, `city` (each storing the member object's `object_id`)

#### Step 2: Define a Collection of Composed Members

Use `@ReflectField(type=FieldType.OBJECTBOX)` for a one-to-many composition. Internally backed by `FieldObjectBox<Ty>`:

````java
public class Company extends Clasz {
    @ReflectField(type=FieldType.STRING, size=64, displayPosition=5)
    public static String CompanyName;

    // Composition: Company "has-a" Addr (single member)
    @ReflectField(type=FieldType.OBJECT, deleteAsMember=true,
        clasz=Addr.class, displayPosition=10)
    public static String Address;

    // Composition: Company "has-many" Employee (collection)
    @ReflectField(type=FieldType.OBJECTBOX,
        clasz=Employee.class, displayPosition=20)
    public static String Employee;
}
````

StORMi creates `iw_company_employee` with columns: parent PK, child `object_id`, and `leaf_class` (for polymorphic support).

#### Step 3: Persist Composed Objects

When `PersistCommit()` is called on the parent, StORMi recursively persists all composed member objects first, then updates the `iv_` or `iw_` link tables:

````java
// Create and populate the composed objects
Addr addr = (Addr) ObjectBase.CreateObject(conn, Addr.class);
addr.setValueStr(Addr.Addr1, "123 Main Street");
addr.setValueStr(Addr.PostalCode, "10001");

Company company = (Company) ObjectBase.CreateObject(conn, Company.class);
company.setValueStr(Company.CompanyName, "Acme Corp");

// Set the single member object
company.setValueObject(conn, Company.Address, addr);

// Add members to the collection
Employee emp1 = (Employee) ObjectBase.CreateObject(conn, Employee.class);
emp1.setName("John Doe");
emp1.setDepartment("Engineering");
company.getFieldObjectBox(Company.Employee).addValueObject(emp1);

// Persist -- StORMi inserts company, addr, emp1, and links them
// via iv_company and iw_company_employee automatically
ObjectBase.PersistCommit(conn, company);
````

#### Step 4: Fetching Composed Members

For `FieldType.OBJECT` fields, fetching behavior depends on the `prefetch` attribute:

- **`prefetch=true`**: The member object is eagerly loaded when the parent is populated.
- **`prefetch=false`** (default): The member is lazy-loaded on first access via `FieldObject.getValueObj(conn)`.

For `FieldType.OBJECTBOX` fields, members are fetched on demand using `forEachMember`:

````java
Company company = (Company) ObjectBase.CreateObject(conn, Company.class);
company.setValueStr(Company.CompanyName, "Acme Corp");
if (company.populate(conn)) {
    // Access single composed member (lazy or prefetched)
    Addr addr = (Addr) company.getValueObject(conn, Company.Address);
    String street = addr.getValueStr(Addr.Addr1);

    // Iterate collection members
    FieldObjectBox<Employee> employees = company.getFieldObjectBox(Company.Employee);
    employees.forEachMember(conn, (Connection c, Employee emp) -> {
        String name = emp.getName();
        // process each employee
        return true; // continue iteration
    });
}
````

#### Step 5: Cascade Deletion with `deleteAsMember`

The `deleteAsMember` attribute controls whether deleting the parent also deletes the composed member:

- **`deleteAsMember=true`**: The member object is deleted when the parent is deleted (true composition/ownership).
- **`deleteAsMember=false`**: The link is removed but the member object is preserved (association/reference).

````java
// With deleteAsMember=true on Address field:
company.deleteCommit(conn);
// Deletes company, removes iv_company link, AND deletes the Addr object

// With deleteAsMember=false on Country field in Addr:
addr.deleteCommit(conn);
// Deletes addr, removes iv_addr link, but Country object is preserved
````

#### Key Rules

1. Composition is declared using `@ReflectField(type=FieldType.OBJECT, clasz=MemberClass.class)` for single members, or `FieldType.OBJECTBOX` for collections.
2. Single member relationships are stored in `iv_[classname]` tables; collections in `iw_[classname]_[fieldname]` tables.
3. `PersistCommit()` recursively persists all composed members before linking them.
4. Use `deleteAsMember=true` for owned members (true composition) and `deleteAsMember=false` for shared/referenced members (association).
5. Use `inline=true` to flatten a composed object's fields directly into the parent table (no separate link table).
6. `FieldObjectBox` cannot be inline.

<br>

### 4. **Polymorphism**

StORMi enables a member field (declared as a base or abstract type) to hold any concrete subclass at runtime. When persisting, StORMi stores the actual concrete class name in a `leaf_class` column alongside the object reference. When fetching, StORMi reads this column and instantiates the correct concrete type automatically.

Polymorphism applies to two field types:
- **`FieldType.OBJECT`** (single reference) — the `iv_` relationship table gains a `leaf_class` column
- **`FieldType.OBJECTBOX`** (collection) — the `iw_` link table always includes a `leaf_class` column

#### How It Works

Polymorphism is activated in two ways:

1. **Explicitly** — set `polymorphic=true` in the `@ReflectField` annotation
2. **Automatically** — if the declared `clasz` is an abstract class, StORMi forces `polymorphic = true`

#### Database Mapping

For a **single object reference** (`FieldType.OBJECT`), StORMi creates an `iv_[classname]` table. When the field is polymorphic, this table includes an additional `leaf_class` column that stores the fully qualified Java class name of the concrete object.

For a **collection** (`FieldType.OBJECTBOX`), StORMi creates an `iw_[classname]_[fieldname]` table with three columns: the parent PK, the child `object_id`, and a `leaf_class` column.

| Table Type | Columns |
|---|---|
| `iv_[classname]` (OBJECT) | parent PK, member object ID, `leaf_class` (if polymorphic) |
| `iw_[classname]_[fieldname]` (OBJECTBOX) | parent PK, member object ID, `leaf_class` |

#### Step 1: Define the Abstract Base Class

````java
import biz.shujutech.db.object.Clasz;
import biz.shujutech.db.relational.FieldType;
import biz.shujutech.reflect.ReflectField;

// Abstract class — no table is created for this class directly.
// Its fields are merged into concrete subclass tables.
public abstract class Party extends Clasz {

    @ReflectField(type = FieldType.STRING, size = 128, displayPosition = 10)
    public static String PartyName;

    public String getPartyName() throws Exception {
        return this.getValueStr(PartyName);
    }

    public void setPartyName(String aName) throws Exception {
        this.setValueStr(PartyName, aName);
    }
}
````

Because `Party` is abstract, StORMi will not create a `cz_party` table. Instead, its fields (`PartyName`) will be merged into the concrete subclass tables.

#### Step 2: Define Concrete Subclasses

````java
// Person -> maps to table "cz_person" with columns: party_name, phone_number
public class Person extends Party {

    @ReflectField(type = FieldType.STRING, size = 20, displayPosition = 20)
    public static String PhoneNumber;

    public String getPhoneNumber() throws Exception {
        return this.getValueStr(PhoneNumber);
    }

    public void setPhoneNumber(String aPhone) throws Exception {
        this.setValueStr(PhoneNumber, aPhone);
    }
}
````

````java
// Organization -> maps to table "cz_organization" with columns: party_name, registration_no
public class Organization extends Party {

    @ReflectField(type = FieldType.STRING, size = 32, displayPosition = 20)
    public static String RegistrationNo;

    public String getRegistrationNo() throws Exception {
        return this.getValueStr(RegistrationNo);
    }

    public void setRegistrationNo(String aRegNo) throws Exception {
        this.setValueStr(RegistrationNo, aRegNo);
    }
}
````

#### Step 3: Define Asset Classes for OBJECTBOX Polymorphism

````java
// Base asset class (concrete, not abstract)
public class Asset extends Clasz {

    @ReflectField(type = FieldType.STRING, size = 64, displayPosition = 10)
    public static String AssetName;

    @ReflectField(type = FieldType.FLOAT, displayPosition = 20)
    public static String AssetValue;

    public String getAssetName() throws Exception {
        return this.getValueStr(AssetName);
    }

    public void setAssetName(String aName) throws Exception {
        this.setValueStr(AssetName, aName);
    }
}
````

````java
// Vehicle is a type of Asset
public class Vehicle extends Asset {

    @ReflectField(type = FieldType.STRING, size = 16, displayPosition = 30)
    public static String PlateNumber;

    public String getPlateNumber() throws Exception {
        return this.getValueStr(PlateNumber);
    }

    public void setPlateNumber(String aPlate) throws Exception {
        this.setValueStr(PlateNumber, aPlate);
    }
}
````

````java
// Building is a type of Asset
public class Building extends Asset {

    @ReflectField(type = FieldType.STRING, size = 128, displayPosition = 30)
    public static String Address;

    public String getAddress() throws Exception {
        return this.getValueStr(Address);
    }

    public void setAddress(String aAddr) throws Exception {
        this.setValueStr(Address, aAddr);
    }
}
````

#### Step 4: Define the Master Class with Polymorphic Fields

````java
public class Company extends Clasz {

    @ReflectField(type = FieldType.STRING, size = 128, displayPosition = 10)
    public static String CompanyName;

    // Polymorphic single-object field: Owner can be a Person or Organization.
    // Since Party is abstract, StORMi automatically sets polymorphic=true.
    @ReflectField(type = FieldType.OBJECT, clasz = Party.class,
        deleteAsMember = true, displayPosition = 20)
    public static String Owner;

    // Polymorphic collection field: Assets can be Vehicle, Building, etc.
    // Since Asset is concrete, we explicitly set polymorphic=true.
    @ReflectField(type = FieldType.OBJECTBOX, clasz = Asset.class,
        polymorphic = true, deleteAsMember = true, displayPosition = 30)
    public static String Assets;

    public String getCompanyName() throws Exception {
        return this.getValueStr(CompanyName);
    }

    public void setCompanyName(String aName) throws Exception {
        this.setValueStr(CompanyName, aName);
    }
}
````

**Key points:**
- The `Owner` field declares `clasz = Party.class`. Since `Party` is abstract, StORMi automatically forces `polymorphic = true` — you don't need to set it explicitly.
- The `Assets` field declares `clasz = Asset.class`. Since `Asset` is a concrete class, you must explicitly set `polymorphic = true` to enable polymorphic behavior.

#### Step 5: Database Tables Generated

StORMi will automatically create the following tables:

| Table | Purpose |
|---|---|
| `cz_company` | Company's own fields (`company_name`) |
| `cz_person` | Person fields (`party_name`, `phone_number`) |
| `cz_organization` | Organization fields (`party_name`, `registration_no`) |
| `cz_asset` | Asset fields (`asset_name`, `asset_value`) |
| `cz_vehicle` | Vehicle fields (`plate_number`) + `ih_vehicle` link to Asset |
| `cz_building` | Building fields (`address`) + `ih_building` link to Asset |
| `iv_company` | Member-of table with columns: `cz_company_pk`, `owner` (object ID), **`owner_leaf_class`** (concrete class name) |
| `iw_company_assets` | Box member table with columns: `cz_company_pk`, `assets` (object ID), **`leaf_class`** (concrete class name) |

The `leaf_class` columns are what enable polymorphism — they store the fully qualified Java class name (e.g., `com.example.Person` or `com.example.Vehicle`).

#### Step 6: Persisting Polymorphic Objects

````java
ObjectBase objectDb = new ObjectBase();
String[] args = { "stormi.properties" };
objectDb.setupApp(args);
objectDb.setupDb();
Connection conn = objectDb.getConnPool().getConnection();

// Create a Company
Company company = (Company) ObjectBase.CreateObject(conn, Company.class);
company.setCompanyName("Acme Corp");

// Create a Person as the Owner (polymorphic — declared type is Party)
Person owner = (Person) ObjectBase.CreateObject(conn, Person.class);
owner.setPartyName("John Smith");
owner.setPhoneNumber("555-1234");

// Set the polymorphic owner field
FieldObject<?> ownerField = (FieldObject<?>) company.getField(Company.Owner);
ownerField.setValueObject(owner);

// Add polymorphic assets
FieldObjectBox<?> assetsField = (FieldObjectBox<?>) company.getField(Company.Assets);

Vehicle truck = (Vehicle) ObjectBase.CreateObject(conn, Vehicle.class);
truck.setAssetName("Delivery Truck");
truck.setPlateNumber("ABC-1234");
assetsField.addValueObjectFreeType(truck);

Building warehouse = (Building) ObjectBase.CreateObject(conn, Building.class);
warehouse.setAssetName("Main Warehouse");
warehouse.setAddress("123 Industrial Ave");
assetsField.addValueObjectFreeType(warehouse);

// Persist — StORMi stores the concrete class names in leaf_class columns
company.persistCommit(conn);
````

When persisted, the `iv_company` table will contain:

| cz_company_pk | owner | owner_leaf_class |
|---|---|---|
| 1 | 42 | `com.example.Person` |

And the `iw_company_assets` table will contain:

| cz_company_pk | assets | leaf_class |
|---|---|---|
| 1 | 10 | `com.example.Vehicle` |
| 1 | 11 | `com.example.Building` |

#### Step 7: Fetching Polymorphic Objects

When you fetch the `Company` object, StORMi automatically resolves the correct concrete types:

````java
Company company = (Company) ObjectBase.CreateObject(conn, Company.class);
company.setCompanyName("Acme Corp");
if (company.populate(conn)) {
    // The Owner field automatically resolves to Person (not Party)
    FieldObject<?> ownerField = (FieldObject<?>) company.getField(Company.Owner);
    Clasz<?> owner = ownerField.getValueObj(conn);

    // owner is actually a Person instance
    if (owner instanceof Person) {
        Person person = (Person) owner;
        String phone = person.getPhoneNumber(); // works!
    } else if (owner instanceof Organization) {
        Organization org = (Organization) owner;
        String regNo = org.getRegistrationNo(); // works!
    }

    // Iterate polymorphic collection — each member is the correct concrete type
    FieldObjectBox<?> assetsField = (FieldObjectBox<?>) company.getField(Company.Assets);
    assetsField.forEachMember(conn, (Connection bConn, Clasz<?> asset) -> {
        if (asset instanceof Vehicle) {
            Vehicle v = (Vehicle) asset;
            System.out.println("Vehicle: " + v.getPlateNumber());
        } else if (asset instanceof Building) {
            Building b = (Building) asset;
            System.out.println("Building: " + b.getAddress());
        }
        return true; // continue iterating
    });
}
````

StORMi's `GetEffectiveClass` reads the `leaf_class` value and uses `Class.forName()` to instantiate the correct type before populating the object.

#### Step 8: Updating Polymorphic Fields

You can change the concrete type of a polymorphic field. For example, changing the owner from a `Person` to an `Organization`:

````java
Company company = (Company) ObjectBase.CreateObject(conn, Company.class);
company.setCompanyName("Acme Corp");
if (company.populate(conn)) {
    // Replace the Person owner with an Organization
    Organization newOwner = (Organization) ObjectBase.CreateObject(conn, Organization.class);
    newOwner.setPartyName("Acme Holdings Ltd");
    newOwner.setRegistrationNo("REG-9876");

    FieldObject<?> ownerField = (FieldObject<?>) company.getField(Company.Owner);
    ownerField.setValueObject(newOwner);

    // Persist — StORMi updates the leaf_class column to Organization
    // If deleteAsMember=true, the old Person object is also deleted
    company.persistCommit(conn);
}
````

StORMi handles updating both the object reference and the `leaf_class` column, and optionally deletes the old member if `deleteAsMember=true`.

#### Key Rules

1. **Abstract declared types are automatically polymorphic.** If the `clasz` in `@ReflectField` is abstract, StORMi forces `polymorphic=true` — no need to set it explicitly.
2. **Concrete declared types require explicit `polymorphic=true`.** If the base class is concrete but you want to store subclasses, you must set `polymorphic=true` in the annotation.
3. **Abstract classes cannot be inline.** StORMi throws an error if you try to combine `inline=true` with an abstract class.
4. **The `leaf_class` column stores the fully qualified class name** (e.g., `com.example.Person`). If you rename or move a class, existing database records will break.
5. **OBJECTBOX collections always support polymorphism** — the `iw_` table always includes a `leaf_class` column, and the fetch logic checks it.
6. **No SQL required** — StORMi handles all DDL (table/column creation) and DML (insert/update/delete) for polymorphic relationships automatically.

<br>

## Contact Us

For any further support, please contact me at shujutech@gmail.com



