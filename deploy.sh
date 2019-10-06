docker build -t aeghobor/multidocker-client:latest -t aeghobor/multidocker-client:$SHA -f ./client/Dockerfile ./client
docker build -t aeghobor/multidocker-server:latest -t aeghobor/multidocker-server:$SHA -f ./server/Dockerfile ./server
docker build -t aeghobor/multidocker-worker:latest -t aeghobor/multidocker-worker:$SHA -f ./worker/Dockerfile ./worker

docker push aeghobor/multidocker-client:latest
docker push aeghobor/multidocker-server:latest
docker push aeghobor/multidocker-worker:latest

docker push aeghobor/multidocker-client:$SHA
docker push aeghobor/multidocker-server:$SHA
docker push aeghobor/multidocker-worker:$SHA

kubectl apply -f k8s
kubectl set image deployments/server-deployment server=aeghobor/multidocker-server:$SHA
kubectl set image deployments/client-deployment client=aeghobor/multidocker-client:$SHA
kubectl set image deployments/worker-deployment worker=aeghobor/multidocker-worker:$SHA
