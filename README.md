
# ⚡ Load Shedding Distributed System

[![Java](https://img.shields.io/badge/Java-17+-brightgreen.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-Build-blue)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A modular Java-based distributed system for managing and displaying load shedding schedules in South Africa. Built using Javalin for REST APIs, Thymeleaf for the web frontend, and Apache ActiveMQ for messaging.


## 📚 Table of Contents

<details>
  <summary>Click to expand</summary>

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

</details>

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
              +-----------------------+
              |         web           |
              | (Javalin + Thymeleaf) |
              +-----------------------+
```

---

## 🧰 Tech Stack

- **Java 17**
- **Maven**
- **Javalin** (HTTP server)
- **Thymeleaf** (HTML templating)
- **Apache ActiveMQ**
- **Picocli** (command-line args)
- **Unirest** (HTTP client)

---

## ✨ Features

- Province/town querying from a CSV
- REST APIs for current stage & schedule
- Thymeleaf frontend for browsing
- Messaging system for real-time updates
- Alerts via `ntfy.sh`

---

## 🧩 Service Descriptions

<details>
  <summary>Click to expand each</summary>

### `places`
- Reads CSV data
- Exposes `/provinces` and `/towns/{province}`

### `stages`
- Provides `/stage` (GET/POST)
- Broadcasts changes to ActiveMQ topic

### `schedule`
- Provides schedule endpoints
- Subscribes to stage updates via topic

### `web`
- Thymeleaf interface
- Requests data from all services
- Displays schedule and handles forms

</details>

---

## 📬 Message Queuing

- **Broker**: Apache ActiveMQ (default: `tcp://localhost:61616`)
- **Credentials**: `admin/admin`
- **Topics/Queues**:
  - `stage` — broadcast updates
  - `alert` — error monitoring

---

## 🛠 Installation & Prerequisites

### Requirements

- Java 17+
- Maven
- Apache ActiveMQ running

### Install ActiveMQ

```bash
brew install activemq   # macOS
# or download from https://activemq.apache.org/

activemq start
```

---

## 🚀 Running the System

Each module is separate. Start ActiveMQ and run:

```bash
cd places
mvn compile exec:java -Dexec.mainClass="wethinkcode.places.PlaceNameService" -Dexec.args="--port 7000 --datafile places.csv"

cd stage
mvn compile exec:java -Dexec.mainClass="wethinkcode.stage.StageService"

cd schedule
mvn compile exec:java -Dexec.mainClass="wethinkcode.schedule.ScheduleService"

cd web
mvn compile exec:java -Dexec.mainClass="wethinkcode.web.WebService"
```

### Who can run this?

Anyone with:
- Java + Maven installed
- ActiveMQ running
- Appropriate access to `places.csv` data

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

1. Fork this repo
2. Create a new branch: `git checkout -b feature/your-feature`
3. Commit: `git commit -am 'Add feature'`
4. Push: `git push origin feature/your-feature`
5. Open a pull request

---

## 🚧 Possible Future Features

- ✅ Real-time notifications via email/SMS
- 📊 Dashboard with graphs and trends
- 🔒 Admin portal to manage data and schedules
- 🧠 Use of ML to predict future load shedding patterns
- ☁️ Deployment using Docker/Kubernetes
- 🔁 Historical data tracking and CSV export

---

## 📄 License

This project is licensed under the MIT License — see [`LICENSE`](LICENSE) for details.
