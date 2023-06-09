import axios from "axios";
import jwt_decode from "jwt-decode";

const endPointlog = "http://localhost:8080/log";

async function getLogging(googleToken) {
  await axios
    .post(endPointlog, {
      clientId: googleToken.clientId,
      credential: googleToken.credential,
      select_by: googleToken.select_by,
    })
    .then((response) => {
      localStorage.setItem("token", response.data.refreshToken);
    })
    .catch((error) => {
      console.error("Error fetching data:", error);
      if (error.response.status === 403) {
        throw new Error("Invalid");
      }
    });
}

export { getLogging };
