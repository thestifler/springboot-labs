package library_api.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;


@RestController 
@RequestMapping("library")
public class LibraryController {

    @GetMapping()
    public ResponseEntity<String> getMethodName() {
        return ResponseEntity.ok("ok");
    }
    
}
