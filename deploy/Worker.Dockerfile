FROM python@sha256:bf12ecc0bea3f171d55145dd02c78b5eeb09f30ee2ac9cc2b4c9e4e8f5e30741
WORKDIR /app
COPY wheels /wheels
RUN pip install --no-cache-dir --no-index --find-links=/wheels httpx==0.28.1 mutagen==1.47.0 && rm -r /wheels
COPY src /app/src
COPY server.py LICENSE-LyricFlow /app/
ENV PYTHONUNBUFFERED=1 MUSIC_PATH=/music STATE_DIR=/state
USER 1000:1000
STOPSIGNAL SIGTERM
CMD ["python", "server.py"]
