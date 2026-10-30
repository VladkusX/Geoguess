FROM python:3.12-slim
WORKDIR /srv
ENV PYTHONUNBUFFERED=1 KUWETA_DB=/data/kuweta.db TZ=Europe/Warsaw
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
VOLUME /data
EXPOSE 8787
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8787", "--proxy-headers"]
