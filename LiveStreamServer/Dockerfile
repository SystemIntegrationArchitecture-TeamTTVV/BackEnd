FROM node:18-alpine

# Install ffmpeg for HLS transcoding
RUN apk add --no-cache ffmpeg

WORKDIR /app
COPY package*.json ./
RUN npm install --production

COPY . .

# Create media output directory
RUN mkdir -p /app/media

EXPOSE 1935 8888

CMD ["node", "server.js"]
