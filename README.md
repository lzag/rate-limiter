# Rate Limiter

Rate limiter written in Kotlin with the Vert.x framework. Uses Redis Lua script for rate limiting.

### [Read the blog post](https://lukaszzagroba.com/rate-limiter-experiments)

## Installation

1. Prerequisites:
   - Java 21
   - Git (to clone the repository)
   - Docker (for running via Docker Compose)

2. Clone the Repository:
   git clone https://github.com/lzag/rate-limiter.git
   cd rate-limiter

3. Adjust the config:
   Configuration can be adjusted in conf/conf.yaml

4. Start the app with Docker:
    ```
    docker-compose up -d
    ````

## Usage

This project implements a rate limiter using different algorithms. The algorithms are located in checkers.lua. One algo needs a regular refill to be run by the app (token bucket).

#### Development

1. Run the Application Locally:
   ./gradlew run

2. Test the Endpoint:
   Send HTTP requests to `http://localhost:8888/`, for example using httpie:
   ```
   http :8888 X-User-Id:1`
   ```

## Motivation

I built this project to explore rate limiting hands-on, using Kotlin and Vert.x, fueled by my fascination with reliable distributed systems that stay robust under load. The goal was to understand how rate limiters protect services, leveraging Redis Lua scripts to explore different limiting implementations. I wanted to see firsthand how they minimize network traffic by throttling requests at the source, reducing the load on downstream services. This project let me experiment with settings like max concurrent requests using Locust load testsing. Lua practice was a bonus—handy for tweaking my NeoVim setup and beyond. I also implemented a circuit breaker in case Redis instance goes down, to prevent cascading failures.

## Testing

The project includes some basic tests (in `src/test/kotlin`) using JUnit 5 and Vert.x testing utilities as well as Lua rate-limiting algos tests (in `src/test/lua`).

Run all tests (starts Redis in Docker automatically and runs Lua test as well):
```
./gradlew test
```

Key tests include:
- Checks rate limiting behavior with a small number of requests.
- Check algorithm behaviour with LuaUnit tests.

## Links

- [An Alternative Approach to Rate Limiting](https://www.figma.com/blog/an-alternative-approach-to-rate-limiting/)
- [Rate Limiting in Node.js](https://blog.logrocket.com/rate-limiting-node-js/)
- [How a Rate Limiter Took Down GitHub](https://www.linkedin.com/pulse/how-rate-limiter-took-down-github-arpit-bhayani/)
- [Simple Rate Limiting Algorithm Gist](https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d)
- [Rate Limiting on Wikipedia](https://en.wikipedia.org/wiki/Rate_limiting)
- [Token Bucket Algorithm](https://en.wikipedia.org/wiki/Token_bucket#Algorithm)
- [Rate Limiters at Stripe](https://stripe.com/blog/rate-limiters)
- [Understanding Rate Limiting (YouTube)](https://youtu.be/FU4WlwfS3G0?feature=shared)
- [Scaling GitHub’s API with a Sharded, Replicated Rate Limiter in Redis](https://github.blog/engineering/how-we-scaled-github-api-sharded-replicated-rate-limiter-redis/)
- [Vert.x Documentation](https://vertx.io/docs/)

## Author

- [Lukasz Zagroba](https://lukaszzagroba.com)
