from locust import HttpUser, task, between, events
from locust.runners import MasterRunner
import time
import logging

class QuickTestUser(HttpUser):
    # Set the host directly in the class
    host = "http://localhost:8888"

    # Wait time between requests for each user (0.01 to 0.05 seconds)
    wait_time = between(0.01, 0.8)

    # Unique user ID counter
    user_id = 0

    def on_start(self):
        # Assign incrementing ID to each user
        self.user_id = QuickTestUser.user_id
        QuickTestUser.user_id += 1

    @task
    def test_endpoint(self):
        # Send request with incrementing x-user-id header
        headers = {"x-user-id": str(self.user_id)}
        self.client.get("/", headers=headers)
