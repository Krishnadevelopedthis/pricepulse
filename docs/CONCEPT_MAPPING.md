# Syllabus concept mapping

Every concept points at code that exists. "Run" = executed during development; "Written" = not executed in the build environment (needs Maven, PostgreSQL, Tomcat or MongoDB).

| Concept | Implementation | Status |
|---|---|---|
| TCP | `academic/networking/TcpDemo.java` (ServerSocket + Socket, accept, request, response, close) | Run |
| UDP | `academic/networking/UdpDemo.java` (DatagramSocket + DatagramPacket, TCP vs UDP notes) | Run |
| URL | `UrlDemo.java` (java.net.URL parts) + `backend/.../util/UrlNormalizer` used for duplicate prevention | Run |
| InetAddress | `InetDiagnostics.java`; `backend/.../util/UrlSafety` (SSRF guard) | Run |
| Thread / Runnable | `concurrency/ThreadLifecycleDemo.java` | Run |
| Thread lifecycle | same file: NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED observed | Run |
| Synchronization | `SynchronizationDemo.java` (lost updates vs synchronized/Atomic/ReentrantLock); production: per-product lock in `ProductService` | Run / Written |
| wait / notify / notifyAll | `WaitNotifyDemo.java` (bounded producer-consumer) | Run |
| Thread pool / Executor framework | `ExecutorDemo.java`; production: `ExecutorConfig` + `PriceCheckScheduler` | Run / Written |
| Concurrency utilities | `ExecutorDemo.java`: Callable, Future, CompletableFuture, ScheduledExecutorService, CountDownLatch, BlockingQueue, ConcurrentHashMap, AtomicInteger | Run |
| JDBC architecture, DriverManager, Connection | `academic/jdbc/.../Db.java` | Compiled |
| Statement | `JdbcDemo.statementCrud` | Written |
| PreparedStatement | `JdbcDemo.preparedStatementAndInjection`, `JdbcPriceHistoryDAO` | Written |
| CallableStatement | `JdbcDemo.callableDemo` + `database/procedure.sql` | Written |
| ResultSet, metadata | `JdbcPriceHistoryDAO.findByProduct`, `JdbcDemo.metadataDemo` (DatabaseMetaData, ResultSetMetaData) | Written |
| Transactions | `JdbcDemo.transactionDemo` (setAutoCommit(false), commit, rollback) | Written |
| Batch | `JdbcDemo.batchDemo` (addBatch, executeBatch) | Written |
| DAO | `PriceHistoryDAO` -> `JdbcPriceHistoryDAO` | Compiled |
| Servlet, lifecycle, GET/POST, ServletContext, init params | `academic/servlets/.../LifecycleServlet.java`, `web.xml` | Written |
| Cookies, HttpSession | `SessionServlet.java` | Written |
| RequestDispatcher | `DispatchServlet.java` (forward / include) | Written |
| Filter, Listener | `RequestLogFilter.java`, `AppListener.java` | Written |
| JSP, JSTL, EL, directives, declaration, expression, errorPage | `WEB-INF/views/*.jsp` | Written |
| MVC | `PriceReportController` -> `PriceReportService` -> `JdbcPriceHistoryDAO` -> `report.jsp` | Written |
| REST | `backend/.../web/ProductController`, `NotificationController` | Written |
| JSON (Jackson) | Spring's Jackson for DTOs; `ProductPageParser` (JSON-LD); `PricePulseClient` (object <-> JSON) | Written |
| Java REST client | `academic/rest-client/.../PricePulseClient.java` (java.net.http: GET, POST, DELETE) | Written |
| Dependency injection | constructor injection across `@Service`, `@Component`, `@Repository`, `@RestController` classes | Written |
| Spring | `backend/` | Written |
