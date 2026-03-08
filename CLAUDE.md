# IPED Project Notes

## Repository
- Upstream: https://github.com/sepinf-inc/IPED.git (remote: origin) — no push access for douglas125
- Fork: https://github.com/douglas125/IPED (remote: fork) — push here, then PR upstream

## Build
- Java 11, Maven multi-module
- `mvn clean install` to build

## Face Recognition Improvement Plan (branch: improve-face-recognition)
Priority order:
1. Swap to InsightFace (ArcFace + RetinaFace + alignment) in FaceRecognitionProcess.py
2. Use Lucene HNSW KNN search in SimilarFacesSearch.java instead of brute-force linear scan
3. Add automatic face clustering (DBSCAN/Chinese Whispers on embeddings)
4. Face quality assessment to skip low-quality detections
5. Batch inference and GPU acceleration improvements

### Key files for face recognition
- `iped-app/resources/scripts/tasks/FaceRecognitionTask.py` — orchestrator
- `iped-app/resources/scripts/tasks/FaceRecognitionProcess.py` — detection + encoding subprocess
- `iped-engine/src/main/java/iped/engine/search/SimilarFacesSearch.java` — matching at query time
- `iped-app/src/main/java/iped/app/ui/SimilarFacesOptionsDialog.java` — face selection UI
- `iped-app/resources/config/conf/FaceRecognitionConfig.txt` — config
- `iped-engine/src/main/java/iped/engine/config/FaceRecognitionConfig.java` — config class
- `iped-engine/src/main/java/iped/engine/task/index/IndexItem.java` — Lucene indexing with KnnVectorField
