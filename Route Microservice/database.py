from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

# MySQL bağlantısı için doğru URL
DATABASE_URL = "mysql+pymysql://fastapi:fastapi123@127.0.0.1:3306/vrp_db"

# SQLAlchemy motoru
engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Tüm modeller için temel sınıf
Base = declarative_base()

# Bağlantıyı yöneten bağımlılık
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
