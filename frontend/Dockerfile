# Step 1: Build the React app
FROM node:18 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN rm -rf node_modules/.vite
RUN rm -rf node_modules
RUN npm install
COPY . .

# Set up environment variables
RUN npm run build

# Step 2: Set up Nginx to serve the build files
FROM nginx:alpine
COPY ./nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
EXPOSE 443

CMD ["nginx", "-g", "daemon off;"]
