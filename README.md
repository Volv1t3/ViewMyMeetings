<body style="font-family: Consolas, sans-serif; font-weight: normal; font-size: 12pt; color: beige">

<blockquote style="font-style: italic; color: whitesmoke"> <blockquote style="font-style: italic; color: whitesmoke; font-size: 9pt; text-align: center"> Hi there! I'm a huge fan of Markdown documents, so apologies in advanced for structuring this as one </blockquote>

***

<h3 style="text-align: center; font-size: large"> ViewMyMeetings: A Client-Server Application for Meeting Management</h3>

<h4 style="text-align: center; font-size: medium"> A comprehensive system for tracking, scheduling and managing meetings across multiple clients</h4>

***

<div style="display: flex; justify-content: center; align-items: center; gap: 10px; margin: 20px 0;">


![Java](https://camo.githubusercontent.com/bea90da226e09b503e6c8fde824f4816b98dcf30cd31e803006bf6335af06890/68747470733a2f2f696d672e736869656c64732e696f2f62616467652f6a6176612d2532334544384230302e7376673f7374796c653d666f722d7468652d6261646765266c6f676f3d6f70656e6a646b266c6f676f436f6c6f723d7768697465)

![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)

![Google Gson](https://img.shields.io/badge/Google_Gson-%23000000.svg?style=for-the-badge&logo=gson&logoColor=white)

![Msgpack](https://img.shields.io/badge/Msgpack-%23000000.svg?style=for-the-badge&logo=mspack&logoColor=white)

</div>

<blockquote style="font-style: italic; color: whitesmoke">
<h2 style="color: beige; font-size: 14pt">&boxUR; Repository Description &boxUL;  </h2>
<p>ViewMyMeetings is a robust client-server application designed for efficient meeting management. Built with Java, it provides a reliable system for scheduling, tracking, and managing meetings across multiple clients. The application features a server component that handles data storage and synchronization, along with client applications that allow users to interact with their meeting schedule.
<br><br>
The system is designed with a distributed architecture that separates server and client components, making it both scalable and fault-tolerant. It features a comprehensive set of meeting management tools including scheduling, notification, and synchronization capabilities.
</p>
<br>
<p>The repository is organized into two main components, with separate directories for server and client applications. Each component is carefully structured to maintain separation of concerns and promote code reusability.
<br>
<br>
Here are some important details for those who wish to explore the files!
</p>
<ul>
<code>File Structure</code>
<li><b>ViewMyMeetingsServer/</b>: The server component responsible for handling meeting data:
    <ul>
    <li><b>src/</b>: Contains the server's source code</li>
    <li><b>Dockerfile</b>: Configuration for containerization</li>
    <li><b>pom.xml</b>: Maven build configuration</li>
    </ul>
</li>
<li><b>ViewMyMeetingsClient/</b>: The client application for end-users:
    <ul>
    <li><b>src/</b>: Contains the client's source code</li>
    <li><b>Dockerfile</b>: Configuration for containerization</li>
    <li><b>pom.xml</b>: Maven build configuration</li>
    </ul>
</li>
</ul>
</blockquote>

***

<blockquote style="font-style: italic; color: whitesmoke">

<h2 style="color: beige; font-size: 14pt">&boxUR; Methodology &boxUL;  </h2>

<p>The application's architecture is divided into two main components:
<br><br>
The server component handles data storage, client authentication, and 
meeting synchronization across multiple clients. It listens on multiple 
ports to provide dedicated connections for each client.
<br><br>
The client applications allow users to view and manage their meetings, and 
communicate with the server to ensure data consistency. The system includes 
several key features:
</p>

<ol>
<li>Client-Server Architecture: Separate components for server and client functionalities</li>
<li>Multi-Client Support: The server can handle multiple simultaneous client connections</li>
<li>Data Persistence: Meeting information is stored in JSON format for reliability</li>
<li>Authentication: Secure client authentication system</li>
<li>Real-time Updates: Changes propagate across the system to maintain consistency</li>
</ol>

<p>The application uses a standard TCP/IP protocol for communication between 
clients and the server, with JSON-formatted data for information exchange.</p>

</blockquote>

***

<blockquote style="font-style: italic; color: whitesmoke">

<h2 style="color: beige; font-size: 14pt">&boxUR; Technologies Used and How to Run &boxUL;  </h2>

<p>The project relies on several key technologies:</p>
<ul>
<li><b>Java 21 based on Amazon Corretto</b> for the core application logic</li>
<li><b>Apache Maven</b> for project management and build automation</li>
<li><b>JSON </b> for data storage and exchange</li>
<li><b>Google Gson</b> for serialization and deserialization of meeting 
information and data transfer</li>
<li><b>Msgpack core</b> for compression and data transfer</li>
<li>Docker for containerization and deployment</li>
</ul>

<p>To run the application:</p>
<ol>
<li>Install Docker and Docker Compose</li>
<li>Clone the repository</li>
<li>Use Docker Compose to start the application:
<br><code>docker-compose up</code>. If you are in an environment and have 
loaded the project <i>as-is</i>, you can also simply run the docker compose 
file from within and it will connect to your Docker Daemon and run the 
project all on its own!
</li>
</ol>

<p>The application will create the necessary directory structure in your Documents folder to store meeting data:</p>
<ul>
<li>Server data: <code>${USERPROFILE}/Documents/ViewMyMeetings/Server</code></li>
<li>Client data: <code>${USERPROFILE}/Documents/ViewMyMeetings/Clients/[client-name]</code></li>
</ul>

</blockquote>

***

<blockquote style="font-style: italic; color: whitesmoke">

<h2 style="color: beige; font-size: 14pt">&boxUR; Docker Hub Repository &boxUL;  </h2>

<p>The application is available as Docker images for easy deployment and usage. The Docker Hub repository contains all necessary images:</p>

<ul>
<li><b>Server Image:</b> <code>arellanosantiago/view-my-meetings:server-latest</code></li>
<li><b>Client Image:</b> <code>arellanosantiago/view-my-meetings:client-latest</code></li>
</ul>

<p>To deploy using Docker:</p>
<ol>
<li>Install Docker on your system</li>
<li>Pull the images from Docker Hub:
<br><code>docker pull arellanosantiago/view-my-meetings:server-latest</code>
<br><code>docker pull arellanosantiago/view-my-meetings:client-latest</code></li>
<li>Use the provided docker-compose.yml file to launch the application:
<br><code>docker-compose up</code></li>
</ol>
<p>The Docker configuration automatically creates the necessary volumes for persistent storage and maps them to the following locations on your local system:</p>
<ul>
<li>Server data: <code>${USERPROFILE}/Documents/ViewMyMeetings/Server</code></li>
<li>Client data: <code>${USERPROFILE}/Documents/ViewMyMeetings/Clients/[client-name]</code></li>
</ul>
</blockquote>

***

<blockquote>
<h2 style="color: beige; font-size: 14pt">&boxUR; Authors &boxUL;  </h2>
<ul>
<li>Marcos Lopez</li>
<li>Santiago Arellano</li>
</ul>
</blockquote>
</blockquote>
</body>