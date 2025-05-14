from sqlalchemy import create_engine, MetaData
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

DATABASE_URL = "mysql+pymysql://fastapi:fastapi123@127.0.0.1:3306/vrp_db"

# Engine ve session oluşturuluyor
engine = create_engine(DATABASE_URL, echo=True)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# SQLAlchemy Base sınıfını tanımlıyoruz
Base = declarative_base()  # Bu satırın var olduğuna emin olun

# Bağlantıyı yöneten bağımlılık fonksiyonu
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
