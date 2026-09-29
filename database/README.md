# Database notes

* **MongoDB is the primary database** of PricePulse (collections `tracked_products`, `price_history`; see `backend/.../model`).
  Indexes are declared on the model classes and created on startup (`auto-index-creation: true`).
* `schema.sql` and `procedure.sql` belong to the **separate academic JDBC module** (`academic/jdbc`) and use PostgreSQL.
  They are not used by the Spring Boot backend.

Setup for the JDBC module:

```bash
createdb pricepulse_academic
psql pricepulse_academic -f database/schema.sql
psql pricepulse_academic -f database/procedure.sql
export DB_URL=jdbc:postgresql://localhost:5432/pricepulse_academic
export DB_USER=...        # never hardcode credentials
export DB_PASSWORD=...
```
