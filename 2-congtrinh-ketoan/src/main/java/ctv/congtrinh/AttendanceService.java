package ctv.congtrinh;

import ctv.congtrinh.AttendanceDAO;
import ctv.congtrinh.AttendanceDAO.Attendance;
import ctv.congtrinh.AttendanceDAO.Result;
import ctv.util.BusinessException;
import ctv.util.Exporter;
import ctv.util.Session;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Nghiệp vụ 2.5–2.6: kiểm tra đầu vào, chấm công, phân loại và xuất báo cáo. */
public class AttendanceService {
    private final AttendanceDAO dao=new AttendanceDAO();
    public List<Attendance> search(String keyword,Integer site,LocalDate from,LocalDate to,String status){
        if(from!=null&&to!=null&&to.isBefore(from))throw new BusinessException("Đến ngày phải sau hoặc bằng Từ ngày.");
        return dao.search(keyword,site,from,to,status);
    }
    public List<Object[]> workers(){return dao.workerOptions();}
    public List<Object[]> sites(){return dao.siteOptions();}
    public void save(Integer id,int worker,int site,LocalDate day,LocalTime in,LocalTime out,BigDecimal regular,BigDecimal overtime){
        if(worker<=0||site<=0)throw new BusinessException("Hãy chọn công nhân và công trình.");
        dao.save(id,worker,site,day,in,out,regular,overtime,Session.userId());
    }
    public void delete(int id){dao.delete(id,Session.userId());}
    public int validate(LocalDate from,LocalDate to){
        if(from==null||to==null||to.isBefore(from))throw new BusinessException("Khoảng thời gian kiểm tra không hợp lệ.");
        return dao.validateRange(from,to);
    }
    public Result weekly(LocalDate from,LocalDate to,Integer worker){range(from,to);return dao.weekly(from,to,worker);}
    public Result byWorksite(LocalDate from,LocalDate to,Integer site){range(from,to);return dao.byWorksite(from,to,site);}
    public Result history(LocalDate from,LocalDate to,Integer worker,Integer site){range(from,to);return dao.history(from,to,worker,site);}
    public void export(Result r,File file,boolean pdf){if(pdf)Exporter.toPdf(file,r.title(),r.headers(),r.rows());else Exporter.toExcel(file,r.title(),r.headers(),r.rows());}
    private static void range(LocalDate f,LocalDate t){if(f==null||t==null||t.isBefore(f))throw new BusinessException("Khoảng thời gian không hợp lệ.");}
}
