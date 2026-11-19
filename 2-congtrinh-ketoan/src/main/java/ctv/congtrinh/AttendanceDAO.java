package ctv.congtrinh;

import ctv.util.BusinessException;
import ctv.util.Ownership;
import ctv.util.DBConnection;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** 2.5–2.6: chấm công, kiểm tra hợp lệ, tổng hợp tuần và báo cáo theo công trình. */
public class AttendanceDAO {
    public record Attendance(int id, int workerId, String workerCode, String workerName,
            int worksiteId, String worksiteCode, String worksiteName, LocalDate workDate,
            LocalTime checkIn, LocalTime checkOut, BigDecimal regular, BigDecimal overtime,
            BigDecimal regularRate, BigDecimal overtimeRate, String status, String note) {}
    public record Result(String title, List<String> headers, List<List<Object>> rows) {}

    private static LocalDate date(ResultSet rs, int i) throws SQLException { return rs.getObject(i, LocalDate.class); }
    private static LocalTime time(ResultSet rs, int i) throws SQLException {
        Time t = rs.getTime(i); return t == null ? null : t.toLocalTime();
    }
    private static final String SELECT = """
        SELECT a.attendance_id,w.worker_id,w.worker_code,w.full_name,s.worksite_id,s.worksite_code,s.worksite_name,
               a.work_date,a.check_in,a.check_out,a.regular_shifts,a.overtime_shifts,a.regular_rate,a.overtime_rate,
               a.validation_status,a.validation_note
        FROM attendances a JOIN workers w ON w.worker_id=a.worker_id JOIN worksites s ON s.worksite_id=a.worksite_id
        """;

    public List<Attendance> search(String keyword, Integer siteId, LocalDate from, LocalDate to, String status) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1=1");
        List<Object> p = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (w.worker_code LIKE ? OR w.full_name LIKE ? OR s.worksite_code LIKE ? OR s.worksite_name LIKE ?)");
            String k="%"+keyword.trim()+"%"; Collections.addAll(p,k,k,k,k);
        }
        if (siteId != null) { sql.append(" AND a.worksite_id=?"); p.add(siteId); }
        if (from != null) { sql.append(" AND a.work_date>=?"); p.add(from); }
        if (to != null) { sql.append(" AND a.work_date<=?"); p.add(to); }
        if (status != null) { sql.append(" AND a.validation_status=?"); p.add(status); }
        sql.append(" ORDER BY a.work_date DESC,w.worker_code");
        return query(sql.toString(), AttendanceDAO::map, p.toArray());
    }

    private static Attendance map(ResultSet r) throws SQLException {
        return new Attendance(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),r.getInt(5),r.getString(6),r.getString(7),
                date(r,8),time(r,9),time(r,10),r.getBigDecimal(11),r.getBigDecimal(12),r.getBigDecimal(13),r.getBigDecimal(14),r.getString(15),r.getString(16));
    }

    public List<Object[]> workerOptions() {
        return query("SELECT worker_id,worker_code,full_name,work_status FROM workers ORDER BY worker_code",
                r -> new Object[]{r.getInt(1),r.getString(2),r.getString(3),r.getString(4)});
    }
    public List<Object[]> siteOptions() {
        return query("SELECT worksite_id,worksite_code,worksite_name FROM worksites ORDER BY worksite_code",
                r -> new Object[]{r.getInt(1),r.getString(2),r.getString(3)});
    }

    public void save(Integer id, int workerId, int siteId, LocalDate day, LocalTime in, LocalTime out,
                     BigDecimal regular, BigDecimal overtime, int userId) {
        if (day == null) throw new BusinessException("Ngày làm việc không được để trống.");
        if (regular == null || overtime == null || regular.signum()<0 || overtime.signum()<0 || regular.add(overtime).signum()<=0)
            throw new BusinessException("Số ca phải không âm và tổng số ca phải lớn hơn 0.");
        if (regular.scale()>1 || overtime.scale()>1) throw new BusinessException("Số ca chỉ được tối đa 1 chữ số thập phân.");
        try (Connection c=DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                if (isLocked(c,day)) throw new BusinessException("Kỳ lương của ngày này đã khóa, không thể sửa chấm công.");
                String old = null;
                if (id != null) {
                    if (!Ownership.isMine(Ownership.ownerIn(c,Ownership.Kind.ATTENDANCE,id))) throw new BusinessException("Bạn chỉ được sửa bản ghi chấm công do chính mình thêm.");
                    try (PreparedStatement ps=c.prepareStatement("SELECT CONCAT_WS('|',worker_id,worksite_id,work_date,check_in,check_out,regular_shifts,overtime_shifts,validation_status),work_date FROM attendances WHERE attendance_id=? FOR UPDATE")) {
                        ps.setInt(1,id); try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new BusinessException("Không tìm thấy bản ghi chấm công.");old=rs.getString(1);
                            LocalDate oldDay=date(rs,2);
                            if(oldDay!=null&&!oldDay.equals(day)&&isLocked(c,oldDay)) throw new BusinessException("Ngày làm việc cũ thuộc kỳ lương đã khóa, không thể chuyển bản ghi sang ngày khác.");}
                    }
                }
                BigDecimal regularRate = rateAt(c,workerId,day,"REGULAR");
                BigDecimal overtimeRate = rateAt(c,workerId,day,"OVERTIME");
                String[] validation = validate(c,workerId,siteId,day,in,out,regular,overtime);
                boolean creating = id == null;
                if (!"INVALID".equals(validation[0]) && regular.signum()>0 && regularRate.signum()<=0) { validation[0]="WARNING"; validation[1] += "; Chưa có đơn giá ca thường hiệu lực tại ngày làm việc"; }
                if (!"INVALID".equals(validation[0]) && overtime.signum()>0 && overtimeRate.signum()<=0) { validation[0]="WARNING"; validation[1] += "; Chưa có đơn giá tăng ca hiệu lực tại ngày làm việc"; }
                if (creating) {
                    try(PreparedStatement ps=c.prepareStatement("""
                        INSERT INTO attendances(worker_id,worksite_id,work_date,check_in,check_out,regular_shifts,overtime_shifts,
                          regular_rate,overtime_rate,validation_status,validation_note,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""",Statement.RETURN_GENERATED_KEYS)) {
                        bind(ps,workerId,siteId,day,in,out,regular,overtime,regularRate,overtimeRate,validation); ps.setInt(12,userId); ps.executeUpdate();
                        try(ResultSet keys=ps.getGeneratedKeys()){if(keys.next())id=keys.getInt(1);}
                    }
                } else {
                    try(PreparedStatement ps=c.prepareStatement("""
                        UPDATE attendances SET worker_id=?,worksite_id=?,work_date=?,check_in=?,check_out=?,regular_shifts=?,overtime_shifts=?,
                          regular_rate=?,overtime_rate=?,validation_status=?,validation_note=? WHERE attendance_id=?""")) {
                        bind(ps,workerId,siteId,day,in,out,regular,overtime,regularRate,overtimeRate,validation); ps.setInt(12,id); ps.executeUpdate();
                    }
                }
                String now = workerId+"|"+siteId+"|"+day+"|"+in+"|"+out+"|"+regular+"|"+overtime+"|"+validation[0];
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO audit_logs(record_id,user_id,action_time,table_name,action_type,old_value,new_value) VALUES(?,?,NOW(),'attendances',?,?,?)")) {
                    ps.setObject(1,id); ps.setInt(2,userId); ps.setString(3,creating?"INSERT":"UPDATE"); ps.setString(4,old); ps.setString(5,now); ps.executeUpdate();
                }
                c.commit();
            } catch(SQLException|RuntimeException e) { c.rollback(); if(e instanceof BusinessException b)throw b; if(e instanceof SQLIntegrityConstraintViolationException)throw new BusinessException("Công nhân đã có chấm công trong ngày này.",e); throw e; }
        } catch(SQLException e) { if (e instanceof SQLIntegrityConstraintViolationException) throw new BusinessException("Công nhân đã có chấm công trong ngày này.",e); throw new BusinessException("Lỗi lưu chấm công: "+e.getMessage(),e); }
    }

    public void delete(int id, int userId) {
        try(Connection c=DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                LocalDate day;
                if (!Ownership.isMine(Ownership.ownerIn(c,Ownership.Kind.ATTENDANCE,id))) throw new BusinessException("Bạn chỉ được xóa bản ghi chấm công do chính mình thêm.");
                try(PreparedStatement ps=c.prepareStatement("SELECT work_date FROM attendances WHERE attendance_id=? FOR UPDATE")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new BusinessException("Không tìm thấy bản ghi.");day=date(rs,1);}}
                if(isLocked(c,day))throw new BusinessException("Kỳ lương đã khóa, không thể xóa chấm công.");
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO audit_logs(record_id,user_id,action_time,table_name,action_type,old_value,new_value) VALUES(?,?,NOW(),'attendances','DELETE',(SELECT CONCAT_WS('|',worker_id,worksite_id,work_date,check_in,check_out,regular_shifts,overtime_shifts) FROM attendances WHERE attendance_id=?),NULL)")){ps.setInt(1,id);ps.setInt(2,userId);ps.setInt(3,id);ps.executeUpdate();}
                try(PreparedStatement ps=c.prepareStatement("DELETE FROM attendances WHERE attendance_id=?")){ps.setInt(1,id);ps.executeUpdate();}
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();if(e instanceof BusinessException b)throw b;throw e;}
        }catch(SQLException e){throw new BusinessException("Lỗi xóa chấm công: "+e.getMessage(),e);}
    }

    private static void bind(PreparedStatement ps,int worker,int site,LocalDate day,LocalTime in,LocalTime out,BigDecimal reg,BigDecimal ot,BigDecimal rr,BigDecimal or,String[] v)throws SQLException{
        ps.setInt(1,worker);ps.setInt(2,site);ps.setObject(3,day);ps.setObject(4,in);ps.setObject(5,out);ps.setBigDecimal(6,reg);ps.setBigDecimal(7,ot);ps.setBigDecimal(8,rr);ps.setBigDecimal(9,or);ps.setString(10,v[0]);ps.setString(11,v[1]);
    }

    private static boolean isLocked(Connection c,LocalDate day)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM payroll_periods WHERE status='CLOSED' AND ? BETWEEN start_date AND end_date")){ps.setObject(1,day);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1)>0;}}
    }

    private static BigDecimal rateAt(Connection c,int worker,LocalDate day,String type)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("""
            SELECT sr.rate FROM worker_skills ws JOIN shift_rates sr ON sr.skill_id=ws.skill_id AND sr.level_id=ws.level_id
            WHERE ws.worker_id=? AND sr.shift_type=? AND sr.effective_date<=?
            ORDER BY sr.effective_date DESC,sr.rate_id DESC LIMIT 1""")){
            ps.setInt(1,worker);ps.setString(2,type);ps.setObject(3,day);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getBigDecimal(1):BigDecimal.ZERO;}
        }
    }

    /** Quy tắc giờ làm: ca thường trong khung 07:00 - 17:00 (8 giờ làm + nghỉ trưa); có tăng ca thì tổng thời gian tối đa 16 giờ. */
    private static final LocalTime REGULAR_START=LocalTime.of(7,0), REGULAR_END=LocalTime.of(17,0);
    private static final long MAX_DAY_MINUTES=16*60;

    private static String[] validate(Connection c,int worker,int site,LocalDate day,LocalTime in,LocalTime out,BigDecimal regular,BigDecimal overtime)throws SQLException{
        List<String> invalid=new ArrayList<>(), warning=new ArrayList<>();
        try(PreparedStatement ps=c.prepareStatement("SELECT work_status FROM workers WHERE worker_id=?")){ps.setInt(1,worker);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new BusinessException("Không tìm thấy công nhân.");if("RESIGNED".equals(rs.getString(1)))invalid.add("Công nhân đã nghỉ việc");else if(!"WORKING".equals(rs.getString(1)))warning.add("Công nhân đang tạm nghỉ");}}
        try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM assignments WHERE worker_id=? AND worksite_id=? AND status='ACTIVE' AND start_date<=? AND COALESCE(expected_end_date,'9999-12-31')>=?")){ps.setInt(1,worker);ps.setInt(2,site);ps.setObject(3,day);ps.setObject(4,day);try(ResultSet rs=ps.executeQuery()){rs.next();if(rs.getInt(1)==0)invalid.add("Công nhân chưa được phân công vào công trình trong ngày này");}}
        if(in==null)warning.add("Thiếu giờ check-in");if(out==null)warning.add("Thiếu giờ check-out");
        if(in!=null&&out!=null&&!out.isAfter(in))invalid.add("Giờ check-out phải sau giờ check-in");
        if(in!=null&&out!=null&&out.isAfter(in)){
            if(java.time.Duration.between(in,out).toMinutes()>MAX_DAY_MINUTES)invalid.add("Thời lượng làm việc vượt quá 16 giờ");
            // Ca thường (không có tăng ca): phải nằm trong khung 07:00 - 17:00 (tối đa 8 giờ làm, đã trừ nghỉ trưa).
            boolean regularOnly=regular!=null&&overtime!=null&&regular.signum()>0&&overtime.signum()==0;
            if(regularOnly&&(in.isBefore(REGULAR_START)||out.isAfter(REGULAR_END)))invalid.add("Ca thường phải nằm trong khung 07:00 - 17:00 (tối đa 8 giờ làm); giờ ngoài khung phải ghi là tăng ca");
        }
        if(regular==null||overtime==null)invalid.add("Thiếu số ca");
        if(regular!=null&&overtime!=null&&regular.add(overtime).signum()<=0)invalid.add("Tổng số ca phải lớn hơn 0");
        if(invalid.isEmpty()&&warning.isEmpty())return new String[]{"VALID","Hợp lệ"};
        if(!invalid.isEmpty())return new String[]{"INVALID",String.join("; ",invalid)+(warning.isEmpty()?"":"; "+String.join("; ",warning))};
        return new String[]{"WARNING",String.join("; ",warning)};
    }

    public int validateRange(LocalDate from,LocalDate to){
        int changed=0;
        for(Attendance a:search(null,null,from,to,null)){
            try(Connection c=DBConnection.get()){
                if(isLocked(c,a.workDate()))continue; // kỳ đã khóa: không được thay đổi dữ liệu chấm công
                String[] v=validate(c,a.workerId(),a.worksiteId(),a.workDate(),a.checkIn(),a.checkOut(),a.regular(),a.overtime());
                BigDecimal rr=rateAt(c,a.workerId(),a.workDate(),"REGULAR"),or=rateAt(c,a.workerId(),a.workDate(),"OVERTIME");
                // Bản ghi lưu lúc chưa có đơn giá (giá 0): cập nhật đơn giá mới hiệu lực tại ngày làm việc để lương không bị tính 0.
                BigDecimal newRr=a.regularRate()!=null&&a.regularRate().signum()>0?a.regularRate():rr;
                BigDecimal newOr=a.overtimeRate()!=null&&a.overtimeRate().signum()>0?a.overtimeRate():or;
                boolean rateChanged=newRr.compareTo(a.regularRate()==null?BigDecimal.ZERO:a.regularRate())!=0||newOr.compareTo(a.overtimeRate()==null?BigDecimal.ZERO:a.overtimeRate())!=0;
                if(!"INVALID".equals(v[0])&&a.regular().signum()>0&&newRr.signum()<=0){v[0]="WARNING";v[1]+="; Chưa có đơn giá ca thường";}
                if(!"INVALID".equals(v[0])&&a.overtime().signum()>0&&newOr.signum()<=0){v[0]="WARNING";v[1]+="; Chưa có đơn giá tăng ca";}
                try(PreparedStatement ps=c.prepareStatement("UPDATE attendances SET validation_status=?,validation_note=?,regular_rate=?,overtime_rate=? WHERE attendance_id=?")){
                    ps.setString(1,v[0]);ps.setString(2,v[1]);ps.setBigDecimal(3,newRr);ps.setBigDecimal(4,newOr);ps.setInt(5,a.id());changed+=ps.executeUpdate();}
                if(rateChanged){
                    try(PreparedStatement ps=c.prepareStatement("INSERT INTO audit_logs(record_id,user_id,action_time,table_name,action_type,old_value,new_value) VALUES(?,?,NOW(),'attendances','UPDATE',?,?)")){
                        ps.setInt(1,a.id());ps.setInt(2,ctv.util.Session.userId());
                        ps.setString(3,"rate="+a.regularRate()+"/"+a.overtimeRate());ps.setString(4,"rate="+newRr+"/"+newOr);ps.executeUpdate();}
                }
            }catch(SQLException e){throw new BusinessException("Lỗi kiểm tra chấm công: "+e.getMessage(),e);}
        }
        return changed;
    }

    public Result weekly(LocalDate from,LocalDate to,Integer workerId){
        return result("Tổng hợp chấm công theo tuần",List.of("Mã CN","Họ tên","Số ngày","Ca thường","Ca tăng ca","Tiền công tạm tính"),"""
            SELECT w.worker_code,w.full_name,COUNT(a.attendance_id),COALESCE(SUM(a.regular_shifts),0),COALESCE(SUM(a.overtime_shifts),0),
             COALESCE(SUM(a.regular_shifts*a.regular_rate+a.overtime_shifts*a.overtime_rate),0)
            FROM attendances a JOIN workers w ON w.worker_id=a.worker_id
            WHERE a.work_date BETWEEN ? AND ? AND a.validation_status='VALID' AND (? IS NULL OR w.worker_id=?)
            GROUP BY w.worker_id,w.worker_code,w.full_name ORDER BY w.worker_code""",from,to,workerId,workerId);
    }
    public Result byWorksite(LocalDate from,LocalDate to,Integer siteId){
        return result("Báo cáo chấm công theo công trình",List.of("Mã CT","Công trình","Số công nhân","Số ngày công","Ca thường","Ca tăng ca","Tiền công"),"""
            SELECT s.worksite_code,s.worksite_name,COUNT(DISTINCT a.worker_id),COUNT(a.attendance_id),COALESCE(SUM(a.regular_shifts),0),COALESCE(SUM(a.overtime_shifts),0),
             COALESCE(SUM(a.regular_shifts*a.regular_rate+a.overtime_shifts*a.overtime_rate),0)
            FROM attendances a JOIN worksites s ON s.worksite_id=a.worksite_id
            WHERE a.work_date BETWEEN ? AND ? AND a.validation_status='VALID' AND (? IS NULL OR s.worksite_id=?)
            GROUP BY s.worksite_id,s.worksite_code,s.worksite_name ORDER BY s.worksite_code""",from,to,siteId,siteId);
    }
    public Result history(LocalDate from,LocalDate to,Integer workerId,Integer siteId){
        return result("Lịch sử chấm công",List.of("Mã CN","Công nhân","Công trình","Ngày","Check-in","Check-out","Ca thường","Ca tăng ca","Đơn giá thường","Đơn giá tăng ca","Trạng thái","Ghi chú"),"""
            SELECT w.worker_code,w.full_name,s.worksite_name,a.work_date,a.check_in,a.check_out,a.regular_shifts,a.overtime_shifts,a.regular_rate,a.overtime_rate,a.validation_status,a.validation_note
            FROM attendances a JOIN workers w ON w.worker_id=a.worker_id JOIN worksites s ON s.worksite_id=a.worksite_id
            WHERE a.work_date BETWEEN ? AND ? AND (? IS NULL OR a.worker_id=?) AND (? IS NULL OR a.worksite_id=?)
            ORDER BY a.work_date DESC,w.worker_code""",from,to,workerId,workerId,siteId,siteId);
    }

    public List<Object[]> auditHistory(int attendanceId){
        return query("SELECT action_time,action_type,old_value,new_value FROM audit_logs WHERE table_name='attendances' AND record_id=? ORDER BY audit_id DESC",
                r->new Object[]{r.getTimestamp(1),r.getString(2),r.getString(3),r.getString(4)},attendanceId);
    }

    private static Result result(String title,List<String> headers,String sql,Object...p){
        List<List<Object>> rows=query(sql,r->{List<Object>x=new ArrayList<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)x.add(r.getObject(i));return x;},p);
        return new Result(title,headers,rows);
    }
    @FunctionalInterface private interface Mapper<T>{T map(ResultSet r)throws SQLException;}
    private static <T> List<T> query(String sql,Mapper<T> mapper,Object...p){
        try(Connection c=DBConnection.get();PreparedStatement ps=c.prepareStatement(sql)){
            for(int i=0;i<p.length;i++)ps.setObject(i+1,p[i]);try(ResultSet rs=ps.executeQuery()){List<T> out=new ArrayList<>();while(rs.next())out.add(mapper.map(rs));return out;}
        }catch(SQLException e){throw new BusinessException("Lỗi dữ liệu chấm công: "+e.getMessage(),e);}
    }
}
