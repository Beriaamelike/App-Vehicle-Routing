import datetime
from typing import Optional
from sqlalchemy import create_engine, Column, Integer, String, Float
from sqlalchemy.orm import declarative_base, sessionmaker
from pydantic import BaseModel
from sqlalchemy.dialects.mysql import DOUBLE
from sqlalchemy import Column, Integer, String, Float, DateTime
from datetime import datetime, timezone

# Veritabanı bağlantısı oluşturulmuştur.
DATABASE_URL = "mysql+pymysql://fastapi:fastapi123@localhost/vrp_db"
engine = create_engine(DATABASE_URL, echo=True)

# SQLAlchemy taban sınıfı tanımlanmıştır.
Base = declarative_base()

# Müşteri oluşturma verileri için Pydantic modeli tanımlanmıştır.
class CustomerCreate(BaseModel):
    customer_id: int
    product_id: int
    xc: float
    yc: float
    demand: int
    ready_time: int
    due_time: int
    service_time: int

# Kullanıcı bilgileri veritabanı modeli olarak tanımlanmıştır.
class UserDetails(Base):
    __tablename__ = 'user_details'  
    user_id = Column(Integer, primary_key=True, index=True)
    name = Column(String(255))
    password = Column(String(255))
    username = Column(String(255))

# Depo oluşturma verileri için Pydantic modeli tanımlanmıştır.
class DepotCreate(BaseModel):
    x: float
    y: float
    capacity: int
    fleet_size: int
    max_working_time: int
    fuel_consumption: float  

# Müşteri tablosu oluşturulmuştur.
class Customer(Base):
    __tablename__ = "customers"
    customer_id = Column(Integer, primary_key=True, index=True, autoincrement=False)  
    product_id = Column(Integer, nullable=False)  
    xc = Column(DOUBLE, nullable=False)
    yc = Column(DOUBLE, nullable=False)
    demand = Column(Integer, nullable=False)
    ready_time = Column(Integer, nullable=False)
    due_time = Column(Integer, nullable=False)
    service_time = Column(Integer, nullable=False)

# Depo tablosu oluşturulmuştur.
class Depot(Base):
    __tablename__ = "depot"
    id = Column(Integer, primary_key=True, index=True)
    x = Column(DOUBLE, nullable=False)
    y = Column(DOUBLE, nullable=False)
    capacity = Column(Integer, nullable=False)
    fleet_size = Column(Integer, nullable=False)
    max_working_time = Column(Integer, nullable=False)
    fuel_consumption = Column(DOUBLE, nullable=False)  

# Rota atamaları için tablo tanımlanmıştır.
class RouteAssignment(Base):
    __tablename__ = "routes"
    id = Column(Integer, primary_key=True, index=True)
    route_number = Column(Integer, nullable=False)
    driver_user_id = Column(Integer)  
    assigned_at = Column(DateTime, default=datetime.now(timezone.utc))

# Rota bilgileri için tablo oluşturulmuştur.
class Route(Base):
    __tablename__ = 'route'
    id = Column(Integer, primary_key=True, index=True)
    customer_id = Column(Integer)
    customer_name = Column(String(255))
    product_id = Column(Integer)
    customer_lat = Column(DOUBLE)
    customer_lon = Column(DOUBLE)
    demand = Column(DOUBLE)

# Yanıtta döndürülecek rota bilgisi modeli tanımlanmıştır.
class RouteInfoResponse(BaseModel):
    route_number: int
    route_duration: float
    route_fuel_cost: float

# ACO algoritması için oluşturulan rota tablosu tanımlanmıştır.
class Aco(Base):
    __tablename__ = "aco_routes"
    id = Column(Integer, primary_key=True, index=True)
    route_number = Column(Integer)
    route_order = Column(Integer)
    customer_id = Column(Integer)
    customer_name = Column(String(255))
    product_id = Column(Integer)
    customer_lat = Column(DOUBLE)
    customer_lon = Column(DOUBLE)
    demand = Column(DOUBLE)
    created_at = Column(DateTime, default=datetime.now(timezone.utc))
    route_duration = Column(Float, nullable=True)
    route_fuel_cost = Column(Float, nullable=True)

# Sürücü atama isteği için Pydantic modeli tanımlanmıştır.
class AssignDriverRequest(BaseModel):
    route_number: int
    driver_user_id: int

# Kullanıcı yanıt modeli tanımlanmıştır.
class UserDetailsResponse(BaseModel):
    user_id: int
    name: str
    username: str

    class Config:
        orm_mode = True

# Kullanıcı ve rol ilişkisi için tablo tanımlanmıştır.
class UserDetailsRoles(Base):
    __tablename__ = 'user_details_roles'
    user_user_id = Column(Integer, primary_key=True, index=True)
    roles_role_id = Column(Integer, primary_key=True, index=True)

# Veritabanı oturumu başlatılmıştır.
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Önceden var olan bazı tablolar kaldırılmıştır.
tables_to_drop = [Customer.__table__, Depot.__table__, RouteAssignment.__table__,
                  Route.__table__, Aco.__table__]

for table in tables_to_drop:
    table.drop(bind=engine, checkfirst=True)

# Tüm tablolar yeniden oluşturulmuştur.
Base.metadata.create_all(bind=engine)

# Başarı mesajı yazdırılmıştır.
print("✅ MySQL tabloları başarıyla oluşturuldu!")
