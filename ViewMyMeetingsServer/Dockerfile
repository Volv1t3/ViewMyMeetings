#? Santiago Arellano - 21st-Apr-2025
#? Definicion de un Archivo docker con ayuda de Claude 3.7 desde IntelliJ para
#? comprender el funcionamiento de este archivo

#? 1. Llamamos a la imagen base, la cual voy a usar GRAALVM
FROM amazoncorretto:24

LABEL maintainer="Santiago Arellano <sanfranmillo@outlook.com>"
LABEL description="The following contains the files related to the ViewMyMeeting server container application"

#? 2. Creamos la estructura de directorios en el contenedor base
WORKDIR /app
RUN mkdir -p /home/app/ViewMyMeetings


#? 3. Copiamos los datos del JAR hacia nuestro docker, como tenemos que trabajar con librerias externas
#? usamos un FatJar o UberJar para pasar las dependencias tambien.
COPY target/ViewMyMeetingsServer-1.0-Generated.jar /app/ViewMyMeetings-1.0-Generated.jar

#? 4. Definimos un volumen que vamos a usar, aqui tenemos que hacer
#? crear un volumen
VOLUME ["/home/app/ViewMyMeetings"]

#? 5. Creamos un symlink dentro del contenedor para que el archivo de configuraciones definido
#? en la app, se guarde y permita cargarlo rapidamente
RUN ln -s /home/app/ViewMyMeetings/serverConfiguration.properties /app/serverConfiguration.properties
RUN ln -s /home/app/ViewMyMeetings/serverSideMeetings.json /app/serverSideMeetings.json

#? 6. Copiamos la configuracion inicial como un fallback para el uso
COPY src/main/resources/serverConfiguration.properties /app/serverConfiguration.properties

#? 7. Exponemos los puertos que va a usar el servidor
EXPOSE  8080 8082 8084 8086 8088 8090

# 8. Definimos un script de inicio para manejar la configuración inicial y generar el archivo de configuración desde variables de entorno
RUN echo '#!/bin/bash' > /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '# Function to check if all required environment variables for a specific client are set' >> /app/entrypoint.sh && \
    echo 'check_client_env_vars() {' >> /app/entrypoint.sh && \
    echo '  local client_num=$1' >> /app/entrypoint.sh && \
    echo '  local name_var="CLIENT_${client_num}_NAME"' >> /app/entrypoint.sh && \
    echo '  local id_var="CLIENT_${client_num}_ID"' >> /app/entrypoint.sh && \
    echo '  local port_var="CLIENT_${client_num}_PORT"' >> /app/entrypoint.sh && \
    echo '  local password_var="CLIENT_${client_num}_PASSWORD"' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '  if [ -z "${!name_var}" ] || [ -z "${!id_var}" ] || [ -z "${!port_var}" ] || [ -z "${!password_var}" ]; then' >> /app/entrypoint.sh && \
    echo '    return 1 # Missing client variables' >> /app/entrypoint.sh && \
    echo '  fi' >> /app/entrypoint.sh && \
    echo '  return 0 # All client variables present' >> /app/entrypoint.sh && \
    echo '}' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '# Function to check if all environment variables are set' >> /app/entrypoint.sh && \
    echo 'check_all_env_vars() {' >> /app/entrypoint.sh && \
    echo '  # Check server configuration variables' >> /app/entrypoint.sh && \
    echo '  if [ -z "$SERVER_ADDRESS" ] || [ -z "$SERVER_PORT" ] || [ -z "$SERVER_BACKLOG" ] || [ -z "$CLIENTS_COUNT" ] || [ -z "$STORAGE_LOCATION" ]; then' >> /app/entrypoint.sh && \
    echo '    echo "Missing server configuration environment variables"' >> /app/entrypoint.sh && \
    echo '    return 1' >> /app/entrypoint.sh && \
    echo '  fi' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '  # Check client configuration variables' >> /app/entrypoint.sh && \
    echo '  for i in $(seq 0 $((CLIENTS_COUNT-1))); do' >> /app/entrypoint.sh && \
    echo '    if ! check_client_env_vars "$i"; then' >> /app/entrypoint.sh && \
    echo '      echo "Missing environment variables for client $i"' >> /app/entrypoint.sh && \
    echo '      return 1' >> /app/entrypoint.sh && \
    echo '    fi' >> /app/entrypoint.sh && \
    echo '  done' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '  return 0 # All environment variables are set' >> /app/entrypoint.sh && \
    echo '}' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '# Initialize configuration based on environment variables or default file' >> /app/entrypoint.sh && \
    echo 'if [ ! -f /home/app/ViewMyMeetings/serverConfiguration.properties ]; then' >> /app/entrypoint.sh && \
    echo '  echo "No server configuration found in volume."' >> /app/entrypoint.sh && \
    echo '  if [ "$REGENERATE_CONFIG" = "true" ] && check_all_env_vars; then' >> /app/entrypoint.sh && \
    echo '    echo "Generating server configuration from environment variables..."' >> /app/entrypoint.sh && \
    echo '    # Generate base configuration' >> /app/entrypoint.sh && \
    echo '    cat > /home/app/ViewMyMeetings/serverConfiguration.properties << EOL' >> /app/entrypoint.sh && \
    echo '# Configurations defined for server initialization' >> /app/entrypoint.sh && \
    echo 'serverSide.serverSocketAddress=$SERVER_ADDRESS' >> /app/entrypoint.sh && \
    echo 'serverSide.serverSocketPort=$SERVER_PORT' >> /app/entrypoint.sh && \
    echo 'serverSide.serverSocketMaxBacklog=$SERVER_BACKLOG' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '# Configuration defined for client accepting' >> /app/entrypoint.sh && \
    echo 'clients.count=$CLIENTS_COUNT' >> /app/entrypoint.sh && \
    echo 'server.storage.location=$STORAGE_LOCATION' >> /app/entrypoint.sh && \
    echo 'EOL' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '    # Add client configurations' >> /app/entrypoint.sh && \
    echo '    for i in $(seq 0 $((CLIENTS_COUNT-1))); do' >> /app/entrypoint.sh && \
    echo '      eval CLIENT_NAME=\${CLIENT_${i}_NAME}' >> /app/entrypoint.sh && \
    echo '      eval CLIENT_ID=\${CLIENT_${i}_ID}' >> /app/entrypoint.sh && \
    echo '      eval CLIENT_PORT=\${CLIENT_${i}_PORT}' >> /app/entrypoint.sh && \
    echo '      eval CLIENT_PASSWORD=\${CLIENT_${i}_PASSWORD}' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '      cat >> /home/app/ViewMyMeetings/serverConfiguration.properties << EOL' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '# Client $i' >> /app/entrypoint.sh && \
    echo 'clients.$i.name=$CLIENT_NAME' >> /app/entrypoint.sh && \
    echo 'clients.$i.id=$CLIENT_ID' >> /app/entrypoint.sh && \
    echo 'clients.$i.update_port=$CLIENT_PORT' >> /app/entrypoint.sh && \
    echo 'clients.$i.password=$CLIENT_PASSWORD' >> /app/entrypoint.sh && \
    echo 'EOL' >> /app/entrypoint.sh && \
    echo '    done' >> /app/entrypoint.sh && \
    echo '  else' >> /app/entrypoint.sh && \
    echo '    echo "Using default configuration file"' >> /app/entrypoint.sh && \
    echo '    cp /app/serverConfiguration.properties /home/app/ViewMyMeetings/serverConfiguration.properties' >> /app/entrypoint.sh && \
    echo '  fi' >> /app/entrypoint.sh && \
    echo 'else' >> /app/entrypoint.sh && \
    echo '  echo "Using existing server configuration file from volume"' >> /app/entrypoint.sh && \
    echo '  if [ "$REGENERATE_CONFIG" = "true" ] && check_all_env_vars; then' >> /app/entrypoint.sh && \
    echo '    echo "Regenerating configuration from environment variables..."' >> /app/entrypoint.sh && \
    echo '    # Generate base configuration' >> /app/entrypoint.sh && \
    echo '    cat > /home/app/ViewMyMeetings/serverConfiguration.properties << EOL' >> /app/entrypoint.sh && \
    echo '# Configurations defined for server initialization' >> /app/entrypoint.sh && \
    echo 'serverSide.serverSocketAddress=$SERVER_ADDRESS' >> /app/entrypoint.sh && \
    echo 'serverSide.serverSocketPort=$SERVER_PORT' >> /app/entrypoint.sh && \
    echo 'serverSide.serverSocketMaxBacklog=$SERVER_BACKLOG' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '# Configuration defined for client accepting' >> /app/entrypoint.sh && \
    echo 'clients.count=$CLIENTS_COUNT' >> /app/entrypoint.sh && \
    echo 'server.storage.location=$STORAGE_LOCATION' >> /app/entrypoint.sh && \
    echo 'EOL' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '    # Add client configurations' >> /app/entrypoint.sh && \
    echo '    for i in $(seq 0 $((CLIENTS_COUNT-1))); do' >> /app/entrypoint.sh && \
    echo '      eval CLIENT_NAME=\${CLIENT_${i}_NAME}' >> /app/entrypoint.sh && \
    echo '      eval CLIENT_ID=\${CLIENT_${i}_ID}' >> /app/entrypoint.sh && \
    echo '      eval CLIENT_PORT=\${CLIENT_${i}_PORT}' >> /app/entrypoint.sh && \
    echo '      eval CLIENT_PASSWORD=\${CLIENT_${i}_PASSWORD}' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '      cat >> /home/app/ViewMyMeetings/serverConfiguration.properties << EOL' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '# Client $i' >> /app/entrypoint.sh && \
    echo 'clients.$i.name=$CLIENT_NAME' >> /app/entrypoint.sh && \
    echo 'clients.$i.id=$CLIENT_ID' >> /app/entrypoint.sh && \
    echo 'clients.$i.update_port=$CLIENT_PORT' >> /app/entrypoint.sh && \
    echo 'clients.$i.password=$CLIENT_PASSWORD' >> /app/entrypoint.sh && \
    echo 'EOL' >> /app/entrypoint.sh && \
    echo '    done' >> /app/entrypoint.sh && \
    echo '  fi' >> /app/entrypoint.sh && \
    echo 'fi' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo '# Initialize serverSideMeetings.json file if it does not exist' >> /app/entrypoint.sh && \
    echo 'if [ ! -f /home/app/ViewMyMeetings/serverSideMeetings.json ]; then' >> /app/entrypoint.sh && \
    echo '  echo "Initializing empty meetings storage..."' >> /app/entrypoint.sh && \
    echo '  echo "[]" > /home/app/ViewMyMeetings/serverSideMeetings.json' >> /app/entrypoint.sh && \
    echo 'fi' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo 'echo "Server configuration:"' >> /app/entrypoint.sh && \
    echo 'cat /home/app/ViewMyMeetings/serverConfiguration.properties' >> /app/entrypoint.sh && \
    echo '' >> /app/entrypoint.sh && \
    echo 'exec java -jar /app/ViewMyMeetings-1.0-Generated.jar "$@"' >> /app/entrypoint.sh && \
    chmod +x /app/entrypoint.sh

# 9. Definimos el comando de inicio
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["/home/app/ViewMyMeetings/serverConfiguration.properties"]
