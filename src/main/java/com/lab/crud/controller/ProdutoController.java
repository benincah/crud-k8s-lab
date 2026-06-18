package com.lab.crud.controller;

import com.lab.crud.model.Produto;
import com.lab.crud.repository.ProdutoRepository;
import io.minio.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    private static final Logger log = LoggerFactory.getLogger(ProdutoController.class);

    private final ProdutoRepository repository;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public ProdutoController(ProdutoRepository repository, MinioClient minioClient) {
        this.repository = repository;
        this.minioClient = minioClient;
    }

    @GetMapping
    public List<Produto> listar() {
        log.info("Listando todos os produtos");
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Produto> buscar(@PathVariable Long id) {
        log.info("Buscando produto id={}", id);
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Produto criar(@RequestBody Produto produto) {
        log.info("Criando produto: {}", produto.getNome());
        return repository.save(produto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Produto> atualizar(@PathVariable Long id, @RequestBody Produto produto) {
        log.info("Atualizando produto id={}", id);
        return repository.findById(id).map(p -> {
            p.setNome(produto.getNome());
            p.setDescricao(produto.getDescricao());
            p.setPreco(produto.getPreco());
            return ResponseEntity.ok(repository.save(p));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        log.info("Deletando produto id={}", id);
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/upload")
    public ResponseEntity<Produto> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws Exception {
        log.info("Upload arquivo '{}' para produto id={}", file.getOriginalFilename(), id);
        Produto produto = repository.findById(id).orElse(null);
        if (produto == null) return ResponseEntity.notFound().build();

        String objectName = id + "/" + file.getOriginalFilename();
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());

        produto.setArquivoUrl(bucket + "/" + objectName);
        return ResponseEntity.ok(repository.save(produto));
    }
}
