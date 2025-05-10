
# ⚡ Load Shedding Distributed System

A modular Java-based distributed system for managing and displaying load shedding schedules in South Africa. It uses Javalin for REST APIs, Thymeleaf for the web frontend, and ActiveMQ for inter-service messaging.

---

## 📚 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Features](#features)
- [Service Descriptions](#service-descriptions)
- [Message Queuing](#message-queuing)
- [Installation & Prerequisites](#installation--prerequisites)
- [Running the System](#running-the-system)
- [API Overview](#api-overview)
- [Contributing](#contributing)
- [License](#license)

---

## 🔍 Overview

The system consists of independent services that:
- Provide towns and provinces (`places`)
- Track and update the load shedding stage (`stages`)
- Generate a load shedding schedule (`schedule`)
- Display information via a web UI (`web`)
- Communicate asynchronously via JMS and ActiveMQ

---

## 🏗 Architecture

```
+-----------+     +------------+     +--------------+
|  places   | <-> |  schedule  | <-> |   stages     |
+-----------+     +------------+     +--------------+
       \________________________________________/
                        |
                        v
              +---------------------+
              |    ActiveMQ Broker  |
              +---------------------+
                        |
                        v
                +-----------------+
                |      web        |
                | (Javalin + Thymeleaf) |
                +-----------------+
```

---

## 🧰 Tech Stack

- **Java 17**
- **Maven** for project management
- **Javalin** for REST APIs
- **Thymeleaf** for rendering HTML pages
- **Apache ActiveMQ** for message passing
- **Picocli** for command-line configuration
- **Unirest** for internal HTTP client requests

---

## ✨ Features

- Query provinces and towns from a CSV-based database
- Get or set the current load shedding stage
- View schedules based on current stage and town
- Real-time stage updates via pub-sub using ActiveMQ
- Alerts sent to `ntfy.sh` for service errors

---

## 🧩 Service Descriptions

### `places`
- Reads place data from a CSV.
- Serves `/provinces` and `/towns/{province}` via HTTP.

### `stages`
- Provides `/stage` (GET) and `/stage` (POST).
- Broadcasts new stage values to a **topic** on ActiveMQ.

### `schedule`
- Provides `/province/town` and `/province/town/stage`.
- Generates mocked or dynamic schedules.
- Subscribes to `stage` topic for updates.

### `web`
- Frontend using Javalin and Thymeleaf.
- Displays the stage, allows province/town selection.
- Subscribes to `stage` topic and listens for alerts.

---

## 📬 Message Queuing

- **Broker:** Apache ActiveMQ (default `tcp://localhost:61616`)
- **Credentials:** `admin/admin`
- **Topics/Queues:**
  - `stage` — for broadcasting load shedding stages
  - `alert` — for internal system alerts

---

## 🛠 Installation & Prerequisites

### General Requirements

- Java 17+ installed
- Maven installed
- Apache ActiveMQ running locally

### Install Apache ActiveMQ

```bash
# Example for Mac (Homebrew)
brew install activemq

# Or manually download and extract from: https://activemq.apache.org/

# Start ActiveMQ (default port: 61616)
activemq start
```

---

## 🚀 Running the System

Each module is a separate Maven project. Run each from its own directory.

### 1. Start ActiveMQ

```bash
activemq start
```

### 2. Build and run individual services

#### Place Name Service

```bash
cd places
mvn compile exec:java -Dexec.mainClass="wethinkcode.places.PlaceNameService" -Dexec.args="--port 7000 --datafile places.csv"
```

#### Stage Service

```bash
cd stage
mvn compile exec:java -Dexec.mainClass="wethinkcode.stage.StageService"
```

#### Schedule Service

```bash
cd schedule
mvn compile exec:java -Dexec.mainClass="wethinkcode.schedule.ScheduleService"
```

#### Web Frontend

```bash
cd web
mvn compile exec:java -Dexec.mainClass="wethinkcode.web.WebService"
```

### Who Can Run These Commands?

Anyone with:
- A compatible Java setup (JDK 17+)
- Maven properly installed
- ActiveMQ running

This is suitable for devs, testers, and infrastructure engineers.

---

## 🌐 API Overview

| Service   | Endpoint                         | Method | Description                          |
|-----------|----------------------------------|--------|--------------------------------------|
| places    | `/provinces`                     | GET    | List all provinces                   |
| places    | `/towns/{province}`              | GET    | Towns in a given province            |
| stages    | `/stage`                         | GET    | Get current stage                    |
| stages    | `/stage`                         | POST   | Set current stage                    |
| schedule  | `/{province}/{town}`             | GET    | Schedule for town (current stage)    |
| schedule  | `/{province}/{town}/{stage}`     | GET    | Schedule for town at specific stage  |
| web       | `/`                              | GET    | Web interface homepage               |

---

## 🤝 Contributing

1. Fork the repo
2. Create your feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -am 'Add feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a pull request

---

## 📄 License

MIT License. See `LICENSE` for details.

