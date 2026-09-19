package com.febrie.demo_bk.file.application;

import com.febrie.demo_bk.file.application.storage.FileStorage;
import com.febrie.demo_bk.file.infrastructure.persistence.FileMapper;
import com.febrie.demo_bk.file.infrastructure.persistence.FileObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileServiceTest {

    @Test
    void deleteTempFilesShouldOnlyDeleteTemporaryObjects() {
        FileStorage fileStorage = mock(FileStorage.class);
        FileMapper fileMapper = mock(FileMapper.class);
        FileService fileService = new FileService(fileStorage, fileMapper);

        FileObject temporary = fileObject(1L, FileService.STATUS_TEMP, "temp.png");
        FileObject bound = fileObject(2L, FileService.STATUS_BOUND, "bound.png");
        when(fileMapper.selectByIds(Set.of(1L, 2L)))
                .thenReturn(List.of(temporary, bound));

        fileService.deleteTempFiles(Set.of(1L, 2L));

        verify(fileStorage).delete("blog/temp.png");
        verify(fileMapper).deleteById(1L);
        verify(fileStorage, never()).delete("blog/bound.png");
        verify(fileMapper, never()).deleteById(2L);
    }

    private FileObject fileObject(long id, int status, String objectKey) {
        FileObject object = new FileObject();
        object.setId(id);
        object.setStatus(status);
        object.setBucketName("blog");
        object.setObjectKey(objectKey);
        return object;
    }
}
