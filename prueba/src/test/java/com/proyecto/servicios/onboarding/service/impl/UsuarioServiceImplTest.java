package com.proyecto.servicios.onboarding.service.impl;

import java.util.Collections;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import com.proyecto.servicios.onboarding.dto.request.UsuarioActualizarRequest;
import com.proyecto.servicios.onboarding.dto.request.UsuarioConsultaRequest;
import com.proyecto.servicios.onboarding.dto.response.UsuarioResponse;
import com.proyecto.servicios.onboarding.entity.Cliente;
import com.proyecto.servicios.onboarding.entity.Usuario;
import com.proyecto.servicios.onboarding.exception.UsuarioNoEncontradoException;
import com.proyecto.servicios.onboarding.mapper.UsuarioMapper;
import com.proyecto.servicios.onboarding.repository.UsuarioRepository;
import com.proyecto.servicios.onboarding.service.PaginacionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock UsuarioMapper mapper;
    @Mock PaginacionFactory paginacionFactory;
    @InjectMocks UsuarioServiceImpl service;

    private Usuario usuario;

    @BeforeEach
    void setup() {
        Cliente c = Cliente.builder().id(1L).activo(Boolean.TRUE).build();
        usuario = Usuario.builder().id(10L).correo("u@test.mx").cliente(c).activo(Boolean.TRUE).build();
    }

    @Test
    void consultar_devuelvePaginaMapeada() {
        PageRequest pr = PageRequest.of(0, 10);
        when(paginacionFactory.toPageable(any())).thenReturn(pr);
        when(usuarioRepository.findAll(any(Specification.class), eq(pr)))
                .thenReturn(new PageImpl<>(List.of(usuario)));
        when(mapper.aResponse(usuario)).thenReturn(
                new UsuarioResponse(10L, 1L, "u@test.mx", Boolean.TRUE, null, null));

        Page<UsuarioResponse> r = service.consultar(new UsuarioConsultaRequest());
        assertThat(r.getContent()).hasSize(1);
        assertThat(r.getContent().get(0).correo()).isEqualTo("u@test.mx");
    }

    @Test
    void actualizarEstatus_cambiaActivoYGuarda() {
        when(usuarioRepository.findByClienteId(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.aResponse(any())).thenReturn(
                new UsuarioResponse(10L, 1L, "u@test.mx", Boolean.FALSE, null, null));

        UsuarioActualizarRequest req = new UsuarioActualizarRequest(1L, Boolean.FALSE);
        UsuarioResponse r = service.actualizarEstatus(req);

        assertThat(r.activo()).isFalse();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void actualizarEstatus_usuarioNoExiste_lanza() {
        UsuarioActualizarRequest req = new UsuarioActualizarRequest(99L, Boolean.TRUE);
        // No necesitamos stubbing: Optional.empty() es el default de Mockito para findByClienteId

        assertThatThrownBy(() -> service.actualizarEstatus(req))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }

    @Test
    void consultar_todosLosFiltros_ejecutaSpecCompleta() {
        UsuarioConsultaRequest req = new UsuarioConsultaRequest();
        req.setCorreo("test@demo.mx");
        req.setActivo(Boolean.TRUE);
        PageRequest pr = PageRequest.of(0, 10);
        when(paginacionFactory.toPageable(any())).thenReturn(pr);
        when(usuarioRepository.findAll(any(Specification.class), eq(pr))).thenAnswer(inv -> {
            Specification<Usuario> spec = inv.getArgument(0);
            Root<Usuario> root = mock(Root.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            spec.toPredicate(root, query, cb);
            return new PageImpl<>(Collections.emptyList());
        });
        service.consultar(req);
    }


    @Test
    void consultar_conCorreoBlank_cubreBranchFalse() {
        UsuarioConsultaRequest req = new UsuarioConsultaRequest();
        req.setCorreo(""); // blank
        PageRequest pr = PageRequest.of(0, 10);
        when(paginacionFactory.toPageable(any())).thenReturn(pr);
        when(usuarioRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(pr))).thenAnswer(inv -> {
            org.springframework.data.jpa.domain.Specification<Usuario> spec = inv.getArgument(0);
            Root<Usuario> root = mock(Root.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            spec.toPredicate(root, query, cb);
            return new PageImpl<>(java.util.Collections.emptyList());
        });
        service.consultar(req);
    }

}
