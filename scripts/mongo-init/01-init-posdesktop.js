db = db.getSiblingDB("posdesktop_media");

db.createUser({
  user: "pos_app",
  pwd: "pos123",
  roles: [
    {
      role: "readWrite",
      db: "posdesktop_media"
    }
  ]
});

db.createCollection("documentos_soporte");

db.documentos_soporte.insertOne({
  inicializado: true,
  creadoEn: new Date(),
  origen: "docker-compose"
});
