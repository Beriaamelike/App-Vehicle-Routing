from sqlalchemy import create_engine, Column, Integer, String, Float
from sqlalchemy.orm import declarative_base, sessionmaker

DATABASE_URL = "mysql+pymysql://fastapi:fastapi123@localhost/vrp_db"

# Initialize the SQLAlchemy engine
engine = create_engine(DATABASE_URL, echo=True)

# Base class for models
Base = declarative_base()

# Route Model
class Route(Base):
    __tablename__ = 'routes'

    id = Column(Integer, primary_key=True, index=True)
    customer_id = Column(Integer)
    customer_name = Column(String(255))  # Specify length here
    customer_lat = Column(Float)
    customer_lon = Column(Float)
    demand = Column(Float)


class Aco(Base):
    __tablename__ = "aco_routes"
    
    id = Column(Integer, primary_key=True, index=True)
    route_number = Column(Integer)
    route_order = Column(Integer)
    customer_id = Column(Integer)
    customer_name = Column(String(255))
    customer_lat = Column(Float)
    customer_lon = Column(Float)
    demand = Column(Float)




# Create a sessionmaker for creating sessions
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Drop existing tables if any
Base.metadata.drop_all(bind=engine)

# Create the tables in the database
Base.metadata.create_all(bind=engine)

print("✅ MySQL tables created successfully!")
