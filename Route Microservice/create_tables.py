import datetime
from typing import Optional
from sqlalchemy import create_engine, Column, Integer, String, Float
from sqlalchemy.orm import declarative_base, sessionmaker
from pydantic import BaseModel
from sqlalchemy.dialects.mysql import DOUBLE
from sqlalchemy import Column, Integer, String, Float, DateTime
from datetime import datetime, timezone


DATABASE_URL = "mysql+pymysql://fastapi:fastapi123@localhost/vrp_db"

# Initialize the SQLAlchemy engine
engine = create_engine(DATABASE_URL, echo=True)

# Base class for models
Base = declarative_base()

class CustomerCreate(BaseModel):
    id: int
    xc: float
    yc: float
    demand: int
    ready_time: int
    due_time: int
    service_time: int


class UserDetails(Base):
    __tablename__ = 'user_details'  # Burada doğru tablonun ismini verdiğinizden emin olun

    user_id = Column(Integer, primary_key=True, index=True)
    name = Column(String(255))
    password = Column(String(255))
    username = Column(String(255))

class DepotCreate(BaseModel):
    x: float
    y: float
    capacity: int
    fleet_size: int
    max_working_time: int  # ✅ Eksik alanı ekledik



class Customer(Base):
    __tablename__ = "customers"

    id = Column(Integer, primary_key=True, index=True, autoincrement=False)
    xc = Column(DOUBLE, nullable=False)
    yc = Column(DOUBLE, nullable=False)
    demand = Column(Integer, nullable=False)
    ready_time = Column(Integer, nullable=False)
    due_time = Column(Integer, nullable=False)
    service_time = Column(Integer, nullable=False)


class Depot(Base):
    __tablename__ = "depot"

    id = Column(Integer, primary_key=True, index=True)
    x = Column(DOUBLE, nullable=False)
    y = Column(DOUBLE, nullable=False)
    capacity = Column(Integer, nullable=False)
    fleet_size = Column(Integer, nullable=False)
    max_working_time = Column(Integer, nullable=False) 



class Route(Base):
    __tablename__ = 'routes'

    id = Column(Integer, primary_key=True, index=True)
    customer_id = Column(Integer)
    customer_name = Column(String(255))
    customer_lat = Column(DOUBLE)
    customer_lon = Column(DOUBLE)
    demand = Column(DOUBLE)


class Aco(Base):
    __tablename__ = "aco_routes"
    
    id = Column(Integer, primary_key=True, index=True)
    route_number = Column(Integer)
    route_order = Column(Integer)
    customer_id = Column(Integer)
    customer_name = Column(String(255))
    customer_lat = Column(DOUBLE)
    customer_lon = Column(DOUBLE)
    demand = Column(DOUBLE)
    created_at = Column(DateTime, default=datetime.now(timezone.utc))



# Create a sessionmaker for creating sessions
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Drop existing tables if any
#Base.metadata.drop_all(bind=engine)

# Create the tables in the database
Base.metadata.create_all(bind=engine)

print("✅ MySQL tables created successfully!")
