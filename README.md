# TrustTherapy.ai 🧠💬

TrustTherapy.ai is an AI-powered chatbot designed to provide accessible mental health and therapy-style support. It combines conversational AI with mood tracking to give users a supportive space to talk through their thoughts and feelings.

## ✨ Features

- **AI Chat** — Real-time conversational support powered by Groq and OpenAI models
- **Mood Tracking** — Log and visualize emotional patterns over time
- **Redis Caching** — Fast session and chat data handling via Upstash Redis
- **Persistent Sessions** — Pick up conversations where you left off
- **Pro Subscription** — Paid tier with premium features, powered by Razorpay payment processing
- **Security & Encryption** — Encrypted data in transit and at rest to protect sensitive conversations


## 📸 Screenshots

| Homepage | Login Screen |
|---|---|
| ![Homepage](<img width="1791" height="931" alt="image" src="https://github.com/user-attachments/assets/8b9e6e95-4105-49ae-a7ee-8c7f86938af5" /> <img width="1640" height="931" alt="image" src="https://github.com/user-attachments/assets/f4b7e776-579a-4458-8789-f689184d875e" />

) | ![Login Screen](<img width="1640" height="931" alt="image" src="https://github.com/user-attachments/assets/d197e2d8-6c91-45c8-ba4c-01bfccb8c295" />
) |

| Chat Screen | Profile View |
|---|---|
| ![Chat Screen](<img width="1913" height="939" alt="image" src="https://github.com/user-attachments/assets/9f4bd764-9924-4872-ab2d-a480f95a732c" />
) | ![Profile View](width="1791" height="931" alt="image" src="https://github.com/user-attachments/assets/a97ad654-5a69-449c-bed9-d038e4c14492" />
) |

| Payment Screen | Memories |
|---|---|
| ![Payment Screen](<img width="1640" height="931" alt="image" src="https://github.com/user-attachments/assets/4b5925ae-165a-4d9b-aa4c-77333c46a2cd" />
)  |



## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React (deployed on [Netlify](https://www.netlify.com/)) |
| Backend | Spring Boot (deployed on [Render](https://render.com/)) |
| Cache/Session Store | Redis via [Upstash](https://upstash.com/) |
| AI/LLM | [Groq](https://groq.com/) + OpenAI API |

## 🚀 Getting Started

### Prerequisites

- Java 17+ and Maven
- Node.js 18+ and npm
- A Redis instance (e.g. [Upstash](https://upstash.com/))
- API keys for Groq and OpenAI

### Backend Setup

```bash
cd Backend
```

Create an `application.properties` (or `.env`, depending on your config setup) with:

```env
GROQ_API_KEY=your_groq_api_key
OPENAI_API_KEY=your_openai_api_key
REDIS_URL=your_upstash_redis_url
REDIS_PASSWORD=your_upstash_redis_password
RAZORPAY_KEY_ID=your_razorpay_key_id
RAZORPAY_KEY_SECRET=your_razorpay_key_secret
```

Run the backend:

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080` (or your configured port).

### Frontend Setup

```bash
cd Frontend
npm install
```

Create a `.env` file with:

```env
REACT_APP_API_BASE_URL=http://localhost:8080
```

Run the frontend:

```bash
npm start
```

The app will be available at `http://localhost:3000`.

## 🌐 Deployment

- **Frontend** is deployed on **Netlify**, connected to auto-deploy from the `main` branch.
- **Backend** is deployed on **Render** as a web service.
- **Redis** is hosted on **Upstash** for low-latency caching of chat sessions and mood data.

Make sure to set the corresponding environment variables (API keys, Redis credentials, and the deployed backend URL) in your Netlify and Render dashboards.

## 💳 Pro Version & Payments

TrustTherapy.ai offers a Pro subscription tier for premium features, with payments handled via **Razorpay**.

- Secure checkout and subscription management through Razorpay's hosted flow
- Payment verification handled server-side via Razorpay webhooks/signature validation — never trust client-side payment confirmations
- Subscription status stored and checked on the backend to gate Pro features

Make sure your Razorpay account is configured with the correct webhook URL pointing to your Render-deployed backend, and that `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` are set as environment variables (never committed to source control).

## 🔒 Security & Encryption

Given the sensitive nature of therapy conversations, TrustTherapy.ai takes data protection seriously:

- **Encryption in transit** — All client-server and server-Redis communication uses TLS/HTTPS
- **Encryption at rest** — Sensitive user data (chat logs, mood entries) is encrypted before persistence
- **Secrets management** — API keys and credentials (Groq, OpenAI, Razorpay, Redis) are stored as environment variables, never hardcoded
- **Payment security** — No card or payment details are stored on TrustTherapy.ai servers; all payment handling is delegated to Razorpay's PCI-DSS compliant infrastructure

## 🤝 Contributing

Contributions, issues, and feature requests are welcome. Feel free to open an issue or submit a pull request.

## 📄 License

This project currently has no license specified. Add a `LICENSE` file to define usage terms.
