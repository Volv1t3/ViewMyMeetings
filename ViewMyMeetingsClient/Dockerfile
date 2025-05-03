#? Santiago Arellano - 21st-Apr-2025
#? Definicion de un Archivo docker con ayuda de Claude 3.7 desde IntelliJ para
#? comprender el funcionamiento de este archivo

#? 1. Llamamos a la imagen base, la cual voy a usar GRAALVM
FROM amazoncorretto:24

LABEL maintainer="Santiago Arellano <sanfranmillo@outlook.com>"
LABEL description="The following contains the files related to the ViewMyMeeting client container application"

#? 2. Creamos la estructura de directorios en el contenedor base
WORKDIR /app
RUN mkdir -p /home/app/ViewMyMeetingsClient


#? 3. Copiamos los datos del JAR hacia nuestro docker, como tenemos que trabajar con librerias externas
#? usamos un FatJar o UberJar para pasar las dependencias tambien.
COPY target/ViewMyMeetingsClient-1.0-Generated.jar /app/ViewMyMeetingsClient-1.0-Generated.jar

#? 4. Definimos un volumen que vamos a usar, aqui tenemos que hacer
#? crear un volumen
VOLUME ["/home/app/ViewMyMeetingsClient"]

#? 5. Creamos un symlink dentro del contenedor para que el archivo de configuraciones definido
#? en la app, se guarde y permita cargarlo rapidamente
RUN ln -s /home/app/ViewMyMeetings/clientConfiguration.properties /app/clientConfiguration.properties
RUN ln -s /home/app/ViewMyMeetingsClient/clientMeetings.json /app/clientMeetings.json


#? 6. Copiamos la configuracion inicial como un fallback para el uso
COPY src/main/resources/clientConfiguration.properties /app/clientConfiguration.properties

#? 7. Exponemos los puertos que va a usar el servidor
EXPOSE  8080 8082 8084 8086 8088 8090

# 7. Definimos un script de inicio para manejar la configuración inicial
RUN echo '#!/bin/bash' > /app/entrypoint.sh && \
    echo '# Create configuration file using environment variables if provided' >> /app/entrypoint.sh && \
    echo 'CONFIG_FILE="/home/app/ViewMyMeetingsClient/clientConfiguration.properties"' >> /app/entrypoint.sh && \
    echo 'if [ ! -f "$CONFIG_FILE" ] || [ "$FORCE_CONFIG_UPDATE" = "true" ]; then' >> /app/entrypoint.sh && \
    echo '  echo "# Client configuration file" > "$CONFIG_FILE"' >> /app/entrypoint.sh && \
    echo '  # Client settings from environment variables with defaults if not provided' >> /app/entrypoint.sh && \
    echo '  echo "client.name=${CLIENT_NAME:-Santiago}" >> "$CONFIG_FILE"' >> /app/entrypoint.sh && \
    echo '  echo "client.id=${CLIENT_ID:-C01A}" >> "$CONFIG_FILE"' >> /app/entrypoint.sh && \
    echo '  echo "client.password=${CLIENT_PASSWORD:-12345}" >> "$CONFIG_FILE"' >> /app/entrypoint.sh && \
    echo '  echo "client.storage.location=/home/app/ViewMyMeetingsClient/clientMeetings.json" >> "$CONFIG_FILE"' >> /app/entrypoint.sh && \
    echo '  # Server connection settings' >> /app/entrypoint.sh && \
    echo '  echo "server.host=${SERVER_HOST:-server}" >> "$CONFIG_FILE"' >> /app/entrypoint.sh && \
    echo '  echo "server.port=${SERVER_PORT:-8080}" >> "$CONFIG_FILE"' >> /app/entrypoint.sh && \
    echo '  echo "Configuration file created with environment variables"' >> /app/entrypoint.sh && \
    echo 'else' >> /app/entrypoint.sh && \
    echo '  echo "Using existing configuration file"' >> /app/entrypoint.sh && \
    echo 'fi' >> /app/entrypoint.sh && \
    echo '# Initialize clientMeetings.json file if it does not exist' >> /app/entrypoint.sh && \
    echo 'if [ ! -f /home/app/ViewMyMeetingsClient/clientMeetings.json ]; then' >> /app/entrypoint.sh && \
    echo '  echo "Initializing empty client meetings storage..."' >> /app/entrypoint.sh && \
    echo '  echo "[]" > /home/app/ViewMyMeetingsClient/clientMeetings.json' >> /app/entrypoint.sh && \
    echo 'fi' >> /app/entrypoint.sh && \
    echo 'echo "Starting ViewMyMeetings Client with configuration: $CONFIG_FILE"' >> /app/entrypoint.sh && \
    echo 'exec java -jar /app/ViewMyMeetingsClient-1.0-Generated.jar "$CONFIG_FILE"' >> /app/entrypoint.sh && \
    chmod +x /app/entrypoint.sh

# 8. Definimos el comando de inicio
ENTRYPOINT ["/app/entrypoint.sh"]
